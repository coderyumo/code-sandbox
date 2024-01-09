package com.yumo.ojsandbox.security;

/**
 * @description:
 * @author: yumo
 * @create: 2024-01-08 17:43
 **/

import cn.hutool.core.io.FileUtil;

import java.nio.charset.Charset;

/**
 * 测试安全管理器
 */
public class TestSecurityManager {

    public static void main(String[] args) {
        System.setSecurityManager(new MySecurityManager());
        FileUtil.writeString("aa", "aaa", Charset.defaultCharset());
    }
}
