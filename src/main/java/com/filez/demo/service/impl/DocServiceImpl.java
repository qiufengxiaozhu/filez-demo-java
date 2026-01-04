package com.filez.demo.service.impl;

import com.filez.demo.common.context.UserContext;
import com.filez.demo.common.listener.FileMonitor;
import com.filez.demo.entity.DocMetaEntity;
import com.filez.demo.entity.SysUserEntity;
import com.filez.demo.model.DocControl;
import com.filez.demo.model.DocExtension;
import com.filez.demo.model.DocMeta;
import com.filez.demo.model.DocPermission;
import com.filez.demo.model.DocWaterMark;
import com.filez.demo.model.Profile;
import com.filez.demo.service.DocControlService;
import com.filez.demo.service.DocMetaService;
import com.filez.demo.service.DocService;
import com.filez.demo.service.SysUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文档服务核心 实现
 */
@Service
@Slf4j
@DependsOn("databaseConfig")
public class DocServiceImpl implements FileAlterationListener, DocService {

	/** 本地文件存储路径 */
	private final String localDir = System.getProperty("user.dir") + File.separator + "local-file";
	/** 文件监视器 */
    private FileMonitor monitor;
	/** 本地文件列表 */
    private File local;

    @Resource
    private SysUserService sysUserService;
    @Resource
    private DocMetaService docMetaService;
    @Resource
    private DocControlService docControlService;

    @PostConstruct
    public void init() {
        local = new File(localDir);
        if (!local.exists() && !local.mkdirs()) {
            log.error("创建目录失败 {}", local.getAbsolutePath());
            return;
        }
        copyInternalLocalFileTo(local);
        refreshFile();
        if (monitor == null) {
            startFileMonitor(local);
        }
    }

    private void startFileMonitor(File file) {
        monitor = new FileMonitor(1000);
        monitor.monitor(file.getAbsolutePath(), this);
        try {
            monitor.start();
        } catch (Exception e) {
            log.error("start monitor error {}", e.getMessage());
        }
    }

    private void refreshFile() {
        try {
            File[] files = local.listFiles();
            if (files == null) return;
            
            for (File file : files) {
                if (file.isDirectory()) {
                    syncUserDirectory(file);
                } else {
                    syncFile(file);
                }
            }
            
            // 清理数据库中不存在的文件记录
            cleanupDeletedFiles();
        } catch (Exception e) {
            log.warn("同步文件失败: {}", e.getMessage());
        }
    }

    /**
     * 同步单个文件到数据库
     */
    private void syncFile(File file) {
        String fileName = file.getName();
        DocMetaEntity existing = docMetaService.getDocMetaByName(fileName);
        
        if (existing != null) {
            // 检查文件是否被修改
            if (file.lastModified() > existing.getModifiedAt().getTime()) {
                DocMeta docMeta = docMetaService.convertToDocMeta(existing);
                docMeta.setModifiedAt(new Date(file.lastModified()));
                docMeta.setSize(file.length());
                docMetaService.updateDocMeta(docMeta);
                log.debug("更新文件元数据: {}", fileName);
            }
        } else {
            // 新文件，创建记录
            createDocMetaForFile(file);
        }
    }

    /**
     * 同步用户目录
     */
    private void syncUserDirectory(File userDir) {
        String userId = userDir.getName();
        SysUserEntity user = sysUserService.getUserById(userId);
        
        if (user == null) {
            // 用户不存在，删除目录
            try {
                FileUtils.deleteDirectory(userDir);
                log.info("删除无效用户目录: {}", userDir.getName());
            } catch (IOException e) {
                log.error("删除目录失败: {}", userDir.getAbsolutePath(), e);
            }
            return;
        }

        File[] userFiles = userDir.listFiles(File::isFile);
        if (userFiles != null) {
            for (File file : userFiles) {
                syncFile(file);
            }
        }
    }

    /**
     * 清理数据库中已删除的文件记录
     */
    private void cleanupDeletedFiles() {
        try {
            List<DocMetaEntity> allDocs = docMetaService.getAllDocMetas();
            for (DocMetaEntity doc : allDocs) {
                File file = new File(doc.getFilepath());
                if (!file.exists()) {
                    docMetaService.deleteDocMetaById(doc.getId());
                    docControlService.deleteByDocId(doc.getId());
                    log.info("清理已删除文件的记录: {}", doc.getName());
                }
            }
        } catch (Exception e) {
            log.warn("清理删除文件记录失败: {}", e.getMessage());
        }
    }

    /**
     * 为文件创建DocMeta记录
     */
    private void createDocMetaForFile(File file) {
        SysUserEntity currentUser = UserContext.getCurrentUser();
        SysUserEntity user;
        
        if (currentUser == null) {
            // 如果没有当前用户（如应用启动时），使用默认管理员用户
            List<SysUserEntity> allUsers = sysUserService.getAllUser();
            if (allUsers.isEmpty()) {
                log.warn("系统中没有用户，跳过文件: {}", file.getName());
                return;
            }
            user = allUsers.get(0); // 使用第一个用户作为默认用户
            log.info("使用默认用户 {} 创建文件记录: {}", user.getName(), file.getName());
        } else {
            user = sysUserService.getUserById(currentUser.getId());
        }

        String docId = getDocId(file.getName());
        
        DocMeta docMeta = DocMeta.builder()
                .id(docId)
                .name(file.getName())
                .createdBy(Profile.convertUserToProfile(user))
                .modifiedAt(new Date(file.lastModified()))
                .permissions(new DocPermission())
                .filepath(file.getAbsolutePath())
                .size(file.length())
                .build();

        docMetaService.saveDocMeta(docMeta);
        log.info("创建新文件记录: {}", file.getName());
    }


    /**
     * 内置一些文件，方便测试
     */
    private void copyInternalLocalFileTo(File destDir) {
        String[] buildFiles = {
                "local-docx.docx",
                "local-pptx.pptx",
                "local-xlsx.xlsx"
        };
        try {
            for (String fileName : buildFiles) {
                // classpath下的文件路径，classpath下的文件路径应始终使用"/"分割
                String sourceFilePath = "internal-local-file" + File.separator + fileName;
                File destFile = new File(destDir, fileName);
                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                try (InputStream resourceAsStream = classLoader.getResourceAsStream(sourceFilePath)) {
		            FileUtils.copyInputStreamToFile(Objects.requireNonNull(resourceAsStream), destFile);
	            }
            }
        } catch (Exception e) {
            log.error("copy internal local file error: {}", e.getMessage());
        }
    }

    /**
     * 根据文档ID获取文档信息
     */
    @Override
    public DocMeta findDocMetaById(String docId) {
        DocMetaEntity entity = docMetaService.getDocMetaById(docId);
        if (entity == null) {
            return null;
        }
        
        DocMeta docMeta = docMetaService.convertToDocMeta(entity);
        SysUserEntity currentUser = UserContext.getCurrentUser();
        if (currentUser == null) return docMeta;

        // 获取用户控制配置并直接合并到DocMeta中
        DocControl control = this.getControl(currentUser.getId(), docId);
        if (control != null) {
            docMeta.setPermissions(control.getDocPermission());
            docMeta.setWaterMark(control.getDocWaterMark());
            docMeta.setExtension(control.getExtension());
            docMeta.setRole(control.getRole());
        }
        return docMeta;
    }

    /**
     * 根据文件ID下载文件
     */
    @Override
    public InputStream getDocById(String docId) {
        DocMetaEntity entity = docMetaService.getDocMetaById(docId);
        if (entity == null) {
            log.warn("can not find docMeta of {}", docId);
            return null;
        }
        File file = new File(entity.getFilepath());
        if (file.exists()) {
            try {
                return new FileInputStream(file);
            } catch (FileNotFoundException e) {
                log.error("文件不存在 {}", file.getAbsolutePath());
            }
        }
        return null;
    }

    /**
     * 根据文档ID和用户ID获取完整的文档信息（包含用户的控制配置）
     */
    @Override
    public DocMeta findDocMetaWithControlById(String docId, String userId) {
        // 获取基础文档元数据
        DocMeta docMeta = findDocMetaById(docId);
        if (docMeta == null) {
            return null;
        }

        // 获取用户的控制配置
        DocControl control = docControlService.getDocControl(userId, docId);
        if (control != null) {
            // 将控制配置信息合并到DocMeta中
            if (control.getDocPermission() != null) {
                docMeta.setPermissions(control.getDocPermission());
            }
            if (control.getExtension() != null) {
                docMeta.setExtension(control.getExtension());
            }
            if (control.getDocWaterMark() != null) {
                docMeta.setWaterMark(control.getDocWaterMark());
            }
            if (control.getRole() != null) {
                docMeta.setRole(control.getRole());
            }
        }

        return docMeta;
    }

    /**
     * 获取文件列表
     */
    @Override
    public List<DocMeta> listFiles() {
        // 简化：直接从数据库查询，不需要refreshFile
        List<DocMetaEntity> entities = docMetaService.getAllDocMetas();
        return entities.stream()
                .map(docMetaService::convertToDocMeta)
                .collect(Collectors.toList());
    }

    /**
     * 上传文件
     */
    @Override
    public DocMeta uploadFile(String docId, InputStream inputStream) {

        String localSaveDir = System.getProperty("user.dir") + File.separator + "old-version";
        File file = new File(localSaveDir);
        if (!file.exists() && !file.mkdirs()) {
            log.error("上传文件时，创建保存目录失败");
            return null;
        }

        DocMetaEntity entity = docMetaService.getDocMetaById(docId);
        if (entity == null) {
            log.error("找不到文档元数据，docId: {}", docId);
            return null;
        }
        
        DocMeta docMeta = docMetaService.convertToDocMeta(entity);
        File doc = new File(docMeta.getFilepath());
        
        // 如果文件已存在，先备份旧版本
        if (doc.exists() && doc.length() > 0) {
            File oldFile = new File(localSaveDir + File.separator + new Date().getTime() + "-" + doc.getName());
            try {
                FileUtils.moveFile(doc, oldFile);
                log.info("备份旧版本文件: {}", oldFile.getName());
            } catch (IOException e) {
                log.error("备份文件失败，失败原因：{}", e.getMessage());
            }
        }

        try (FileOutputStream fos = new FileOutputStream(doc)) {
            IOUtils.copy(inputStream, fos);
            docMeta.setModifiedAt(new Date(doc.lastModified()));
            docMeta.setSize(doc.length());
            // 更新数据库
            docMetaService.updateDocMeta(docMeta);
        } catch (IOException e) {
            log.warn("文件保存失败，失败原因：{}", e.getMessage());
        }
        return docMeta;
    }

    /**
     * 在指定路径创建空文件
     */
    @Override
    public DocMeta makeNewFile(String name, String path) throws IOException {

        // 1. 检查是否允许在指定位置保存文件
        SysUserEntity user = UserContext.getCurrentUser();
        if (user == null) {
            log.error("目前未获取到您的身份信息，所以不能保存文件");
            return null;
        }
        if (StringUtils.isEmpty(path)) {
            path = UserContext.getCurrentUser().getId();
        }

        // 检查数据库中是否已存在同名文件
        DocMetaEntity existingEntity = docMetaService.getDocMetaByName(name);
        if (existingEntity != null) {
            log.warn("数据库中已存在同名文件: {}", name);
            return null;
        }

        // 在local-file/userId下创建文件
        Path filePath = Paths.get(System.getProperty("user.dir"), "local-file", path, name);
        File file = filePath.toFile();
        if (file.exists()) {
            log.warn("文件系统中已存在文件: {}", file.getAbsolutePath());
            return null;
        }

        // 2. 创建空文件
        String id = getDocId(name);
        Profile profile = Profile.convertUserToProfile(user);
        if (profile == null) {
            log.error("无法获取当前用户的配置信息");
            return null;
        }
        DocMeta docMeta = new DocMeta(id, name, profile, new Date(), new DocPermission());
        docMeta.setFilepath(file.getAbsolutePath());

        FileUtils.writeStringToFile(file, "", "utf-8", false);
        docMeta.setSize(file.length());

        // 保存到数据库
        docMetaService.saveDocMeta(docMeta);

        return docMeta;
    }

    private String getDocId(String name) {
        if (name.toLowerCase().startsWith("local")) {
            return FilenameUtils.getBaseName(name);
        }
        return "local-" + UUID.randomUUID();
    }

    /**
     * 设置文档的控制权限
     */
    public DocControl getControl(String userId, String fileId) {
        DocControl control = docControlService.getDocControl(userId, fileId);
        
        if (control == null) {
            // 创建默认配置
            DocPermission defaultPermission = new DocPermission();
            // 设置文档下载、打印权限
            // defaultPermission.setDownload(true);
            // defaultPermission.setPrint(true);
            DocWaterMark defaultWatermark = new DocWaterMark();
            DocExtension defaultDocExtension = new DocExtension();
            // 开启doc文档强制修订模式
            // defaultDocExtension.setTrackChangeForceOn(true);
            DocControl defaultControl = new DocControl();
            defaultControl.setDocPermission(defaultPermission);
            defaultControl.setDocWaterMark(defaultWatermark);
            defaultControl.setExtension(defaultDocExtension);
            // defaultControl.setRole("commenter");
            
            // 保存默认配置到数据库
            try {
                docControlService.saveDocControl(userId, fileId, defaultControl);
                log.debug("为用户 {} 文档 {} 创建默认控制配置", userId, fileId);
            } catch (Exception e) {
                log.warn("保存默认控制配置失败: {}", e.getMessage());
            }
            
            return defaultControl;
        }
        
        return control;
    }

    public void updateControl(String userId, String fileId, DocControl controlVO) {
        try {
            docControlService.updateDocControl(userId, fileId, controlVO);
            log.debug("更新用户 {} 文档 {} 的控制配置成功", userId, fileId);
        } catch (Exception e) {
            log.error("更新控制配置失败，用户: {}, 文档: {}, 错误: {}", userId, fileId, e.getMessage());
        }
    }

    /**
     * 根据文档ID删除文件
     */
    @Override
    public DocMeta deleteFileByDocId(String docId) {
        DocMetaEntity entity = docMetaService.getDocMetaById(docId);
        if (entity == null) {
            log.warn("未找到文档元数据，docId: {}", docId);
            return null;
        }
        
        DocMeta docMeta = docMetaService.convertToDocMeta(entity);
        String filepath = docMeta.getFilepath();
        if (StringUtils.isEmpty(filepath)) {
            // 拼接路径删除，文件路径为localDir拼接文件名
            filepath = localDir + File.separator + docMeta.getName();
        }
        File file = new File(filepath);
        if (!file.exists()) {
            log.info("未找到文件 {}", file.getName());
            // 即使文件不存在，也要从数据库删除记录
            docMetaService.deleteDocMetaById(docId);
            return docMeta;
        }
        log.info("找到文件，准备删除文件 {}", file.getName());
        if (file.delete()) {
            log.info("删除文件成功 {}", file.getName());
        }
        // 如果文件不存在了，从数据库中移除相关数据
        if (!file.exists()) {
            docMetaService.deleteDocMetaById(docId);
            // 同时删除文档控制配置
            try {
                docControlService.deleteByDocId(docId);
                log.debug("删除文档控制配置成功，ID: {}", docId);
            } catch (Exception e) {
                log.warn("删除文档控制配置失败，ID: {}, 错误: {}", docId, e.getMessage());
            }
        }

        return docMeta;
    }

    /**
     * 创建文件
     * @param file The file created
     */
    @Override
    public void onFileCreate(File file) {
        log.info("文件 [{}] 已被创建", file.getName());
    }

    /***
     * 文件修改
     * @param file The file modified
     */
    @Override
    public void onFileChange(File file) {
        String fileName = file.getName();
        log.info("文件 [{}] 被修改，路径：{}，大小：{} bytes", fileName, file.getAbsolutePath(), file.length());

        DocMetaEntity entity = docMetaService.getDocMetaByName(fileName);
        if (entity != null) {
            DocMeta docMeta = docMetaService.convertToDocMeta(entity);
            docMeta.setModifiedAt(new Date(file.lastModified()));
            docMeta.setSize(file.length());
            docMetaService.updateDocMeta(docMeta);
            log.info("文件 [{}] 修改时间已更新", fileName);
        }
    }

    /**
     * 更新文档meta
     */
    @Override
    public DocMeta updateDocMeta(DocMeta docMeta) {
        docMetaService.updateDocMeta(docMeta);
        return findDocMetaById(docMeta.getId());
    }

    /**
     * 删除文件
     * @param file The file deleted
     */
    @Override
    public void onFileDelete(File file) {
        log.info("文件 [{}] 已被删除", file.getName());
        listFiles();
    }

    @Override
    public void onStart(FileAlterationObserver fileAlterationObserver) { }

    @Override
    public void onDirectoryCreate(File file) { }

    @Override
    public void onDirectoryChange(File file) { }

    @Override
    public void onDirectoryDelete(File file) { }

    @Override
    public void onStop(FileAlterationObserver fileAlterationObserver) { }

    @Override
    public boolean isAllowedAccess(String docId) {
        return true;
    }

}
