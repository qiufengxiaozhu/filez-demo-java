package com.filez.demo.service;

import com.filez.demo.model.DocControl;
import com.filez.demo.model.DocMeta;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface DocService {

    /**
     * 根据文件ID下载文件
     */
    InputStream getDocById(String docId);

    /**
     * 根据文档ID获取文档信息
     */
    DocMeta findDocMetaById(String docId);

    /**
     * 根据文档ID和用户ID获取完整的文档信息（包含用户的控制配置）
     */
    DocMeta findDocMetaWithControlById(String docId, String userId);

    /**
     * 更新文档meta
     */
    DocMeta updateDocMeta(DocMeta docMeta);

    /**
     * 获取文件列表
     */
    List<DocMeta> listFiles();

    /**
     * 上传文件
     */
    DocMeta uploadFile(String docId, InputStream inputStream);
    /**
     * 根据文档ID删除文件
     */
    DocMeta deleteFileByDocId(String docId);

    /**
     * 在指定路径创建空文件
     */
    DocMeta makeNewFile(String name, String path) throws IOException;

    /**
     * 判断用户是否有权访问对应文件ID
     */
    boolean isAllowedAccess(String docId);

    /**
     * 获取用户控制功能
     */
    DocControl getControl(String userId, String fileId);

    /**
     * 更新用户控制功能
     */
    void updateControl(String userId, String fileId, DocControl controlVO);
}
