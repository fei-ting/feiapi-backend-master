package com.feiting.feiapiinterface.controller;

import com.feiting.feiapiclientsdk.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 名称测试接口控制器。
 *
 * @author feiting
 */
@RestController
@RequestMapping("/name")
public class NameController {

    /**
     * 文本响应媒体类型，显式声明 UTF-8 避免中文响应乱码。
     */
    private static final MediaType TEXT_PLAIN_UTF8 = MediaType.parseMediaType("text/plain;charset=UTF-8");

    /**
     * 通过 GET 请求返回名称。
     *
     * @param name 名称
     * @return 名称响应文本
     */
    @GetMapping("/get")
    public String getNameByGet(String name) {
        return "Get 你的名字是" + name;
    }

    /**
     * 通过 POST 表单参数返回名称。
     *
     * @param name 名称
     * @return 名称响应文本
     */
    @PostMapping("/post")
    public String getNameByPost(@RequestParam String name) {
        return "Post 你的名字是" + name;
    }

    /**
     * 通过 POST JSON 请求体返回用户名。
     *
     * @param user    用户请求对象
     * @param request HTTP 请求对象
     * @return 用户名响应文本
     */
    @PostMapping("/user")
    public ResponseEntity<String> getUsernameByPost(@RequestBody User user, HttpServletRequest request) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return ResponseEntity.badRequest()
                    .contentType(TEXT_PLAIN_UTF8)
                    .body("username 不能为空");
        }
        return ResponseEntity.ok()
                .contentType(TEXT_PLAIN_UTF8)
                .body("Post 用户名字是" + user.getUsername());
    }
}
