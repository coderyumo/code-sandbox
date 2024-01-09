package com.yumo.ojsandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.yumo.ojsandbox.model.ExecuteCodeRequest;
import com.yumo.ojsandbox.model.ExecuteCodeResponse;
import com.yumo.ojsandbox.model.ExecuteMessage;
import com.yumo.ojsandbox.model.JudgeInfo;
import com.yumo.ojsandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: yumo
 * @create: 2024-01-08 19:41
 **/
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox{

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    /**
     * 1、保存文件
     * @param code
     * @return
     */
    public File saveCodeToFile(String code){
        // 1、判断文件夹是否存在
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        // 2、判断全局代码目录是否存在
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }
        // 3、把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_JAVA_CLASS_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2、编译代码
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage compileFile(File userCodeFile){

        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            if (executeMessage.getExitValue() != 0){
                throw new RuntimeException("编译错误");
            }
            return executeMessage;
        } catch (IOException e) {
//           return getErrorResponse(e);
            throw new RuntimeException();
        }
    }

    /**
     * 3、执行代码
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile,List<String> inputList){
        String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
        // 3、执行代码，得到输出结果
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s Main %s", userCodeParentPath, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 创建一个守护线程来监听执行代码的线程时间
                new Thread(() -> {
                    try {
                        Thread.sleep(TIME_OUT);
                        System.out.println("超时啦！！！");
                        runProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
                System.out.println("executeMessage = " + executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                throw new RuntimeException("代码执行异常"+e.getMessage());
            }
        }
        return executeMessageList;
    }

    /**
     * 4、获取输出结果
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时
        long maxTime = 0;
        for (ExecuteMessage executeMessage : executeMessageList) {
            if (StrUtil.isNotBlank(executeMessage.getErrorMessage())) {
                String errorMessage = executeMessage.getErrorMessage();
                executeMessage.setErrorMessage(errorMessage);
                // 执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            // 正确信息返回
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
        }

        //正常运行完成
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        // todo
//        judgeInfo.setMemory();
        judgeInfo.setTime(maxTime);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5、删除文件
     * @param userCodeFile
     * @return
     */
    public Boolean deleteFile(File userCodeFile){
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
            return del;
        }
        return true;
    }

    /**
     * 6、获取错误响应
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        // 1、保存文件
        File userCodeFile = saveCodeToFile(code);
        // 2、编译代码
        ExecuteMessage compileFileExecuteMessage = compileFile(userCodeFile);
        System.out.println(compileFileExecuteMessage);
        // 3、执行代码
        List<ExecuteMessage> executeMessageList = runFile(userCodeFile, inputList);
        // 4、整理输出
        ExecuteCodeResponse outputResponse = getOutputResponse(executeMessageList);
        // 5、删除文件
        Boolean result = deleteFile(userCodeFile);
        if (!result){
            log.error("deleteFile error, userCodeFilePath={}",userCodeFile.getAbsolutePath());
        }
        return outputResponse;
    }


}
