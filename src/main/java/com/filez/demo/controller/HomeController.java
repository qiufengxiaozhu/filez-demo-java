package com.filez.demo.controller;

import com.filez.demo.common.aspect.Log;
import com.filez.demo.common.context.UserContext;
import com.filez.demo.entity.SysUserEntity;
import com.filez.demo.model.DocControl;
import com.filez.demo.model.DocMeta;
import com.filez.demo.model.Profile;
import com.filez.demo.service.DocService;
import com.filez.demo.service.SysUserService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Api(tags = "业务系统控制器")
@Controller
@Slf4j
@RequestMapping("/home")
public class HomeController {

    @Resource
    private DocService docService;
    @Resource
    private SysUserService sysUserService;

    @Log("跳转至home首页")
    @ApiOperation(value = "/home,跳转至home首页")
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("frameUrl", "/home/local");
        return "home";
    }

    @Log("跳转至三方文件列表页")
    @ApiOperation(value = "/home/local,跳转至三方文件列表页")
    @GetMapping("/local")
    public String thirdHome(Model model) {
        model.addAttribute("parentDirs", new String[]{""});
        model.addAttribute("drive", "local");

        SysUserEntity user = UserContext.getCurrentUser();
        try {
            if (docService != null && user != null) {
                String userId = user.getId();
                String shareUid = "share";
                String adminUid = "admin";
                // 按照修改时间倒序排列，如果没有修改时间，则按照创建时间倒序排列
                model.addAttribute("files", docService.listFiles().stream()
                        .filter(meta -> {
                            if (shareUid.equals(userId) || adminUid.equals(userId)) {
                                return true;
                            }
                            Profile createdBy = meta.getCreatedBy();
                            if (createdBy != null) {
                                return userId.equals(createdBy.getId()) || shareUid.equals(createdBy.getId());
                            }
                            Profile owner = meta.getOwner();
                            return owner != null && (userId.equals(owner.getId()) || shareUid.equals(owner.getId()));
                        }).sorted((o1, o2) -> {
                            if (o1.getModifiedAt() != null && o2.getModifiedAt() != null) {
                                return o2.getModifiedAt().compareTo(o1.getModifiedAt());
                            } else if (o1.getCreatedAt() != null && o2.getCreatedAt() != null) {
                                return o2.getCreatedAt().compareTo(o1.getCreatedAt());
                            } else {
                                return 0;
                            }})
                        .collect(Collectors.toList()));
            } else {
                model.addAttribute("files", Collections.emptyList());
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            model.addAttribute("files", Collections.emptyList());
            model.addAttribute("forceLoginWarn", true);
        }

        return "drive";
    }

    @Log("跳转至批量操作页面")
    @ApiOperation(value = "/home/local/batch,跳转至批量操作页面")
    @GetMapping("/local/batch")
    public String localBatchOp(Model model) {
        model.addAttribute("files", docService.listFiles());
        model.addAttribute("drive", "local");

        return "localBatchOp";
    }

    @Log("跳转至用户信息页面")
    @ApiOperation(value = "/home/user,跳转至用户信息页面")
    @GetMapping("/user")
    public String user(Model model) {
        SysUserEntity currentUser = UserContext.getCurrentUser();
        if  (currentUser == null) {
            log.warn("用户未登录");
            return "redirect:/login";
        }
        model.addAttribute("user", sysUserService.getUserById(currentUser.getId()));
        return "user";
    }

    @Log("使用inFrame方式进行编辑预览的接口")
    @ApiOperation(value = "/home/iframe,返回一个页面，用于在其中嵌入编辑预览页面")
    @GetMapping("/iframe")
    public String getOfficeServiceUrl(Model model, @RequestParam(name = "url") String url) throws URISyntaxException, UnsupportedEncodingException {
        URI uri = new URI(url);
        String urlBase = uri.getScheme() + "://" + uri.getHost()
                + (uri.getPort() != -1 ? ":" + uri.getPort() : "")
                + uri.getPath();

        String query = uri.getRawQuery(); // e.g. "repoId=…&action=…"
        Map<String, String> map = splitQuery(query);

        model.addAttribute("urlBase", urlBase);
        model.addAttribute("repoId", map.get("repoId"));
        model.addAttribute("action", map.get("action"));
        model.addAttribute("docId", map.get("docId"));
        model.addAttribute("downloadUrl", map.get("downloadUrl"));
        model.addAttribute("uploadUrl", map.get("uploadUrl"));
        model.addAttribute("userinfo", map.get("userinfo"));
        model.addAttribute("meta", map.get("meta"));
        model.addAttribute("params", map.get("params"));
        model.addAttribute("ts", map.get("ts"));
        model.addAttribute("HMAC", map.get("HMAC"));
        return "/zOffice";
    }

    private static Map<String, String> splitQuery(String query) throws UnsupportedEncodingException {
        Map<String, String> queryPairs = new LinkedHashMap<>();
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
            String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
            queryPairs.put(key, value);
        }
        return queryPairs;
    }

    @Log("更新用户信息接口")
    @ApiOperation(value = "/home/user，更新用户信息接口")
    @PostMapping("/user")
    public String updateUser(@RequestParam SysUserEntity user) {
        sysUserService.updateUserById(user);
        return "redirect:/home/user";
    }

    @Log("更新文档控制功能接口")
    @ApiOperation(value = "/home/control/{userId}/{docId}，更新文档控制功能接口")
    @PostMapping("/control/{userId}/{docId}")
    @ResponseBody
    public String updateControl(@PathVariable String userId, @PathVariable String docId, DocControl controlVO) {
        docService.updateControl(userId, docId, controlVO);
	    return "更新成功";
    }

    @Log("查询文档元数据")
    @ApiOperation(value = "/home/meta/{docId},查询文档元数据")
    @GetMapping("/meta/{docId}")
    public String updateMeta(@PathVariable String docId, Model model) {
        SysUserEntity user = UserContext.getCurrentUser();
        if (user != null) {
            model.addAttribute("controlUrl", String.format("/home/control/%s/%s", user.getId(), docId));
            model.addAttribute("control", docService.getControl(user.getId(), docId));
        }
        model.addAttribute("metaUrl", String.format("/meta/%s", docId));
        model.addAttribute("meta", docService.findDocMetaById(docId));
        return "control";
    }

    @Log("更新文档元数据")
    @ApiOperation(value = "/home/meta/{docId},更新文档元数据")
    @PostMapping("/meta/{docId}")
    public String updateMeta(@PathVariable String docId, DocMeta docMeta, HttpServletRequest request) {
        docService.updateDocMeta(docMeta);
	    log.info("更新成功：{}", docId);
        return "redirect:" + request.getRequestURI();
    }

    @Log("文档比对")
    @ApiOperation(value = "/compare/,文档比对")
    @GetMapping("/compare")
    public String compareDoc() {
        return "compare";
    }
}
