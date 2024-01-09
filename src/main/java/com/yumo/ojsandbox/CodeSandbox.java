package com.yumo.ojsandbox;


import com.yumo.ojsandbox.model.ExecuteCodeRequest;
import com.yumo.ojsandbox.model.ExecuteCodeResponse;

/**
 * @description:
 * @author: yumo
 * @create: 2024-01-06 18:08
 **/
public interface CodeSandbox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);

}
