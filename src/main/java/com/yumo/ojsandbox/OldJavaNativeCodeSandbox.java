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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * @description:
 * @author: yumo
 * @create: 2024-01-06 23:33
 **/
public class OldJavaNativeCodeSandbox implements CodeSandbox {

    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";

    private static final long TIME_OUT = 5000L;

    private static final List<String> blackList = Arrays.asList("Files", "exec");

    private static final String SECURITY_MANAGER_CLASSNAME = "MySecurityManager";

    private static final String SECURITY_MANAGER_PATH = "E:\\yupipro\\oj\\yuoj-code-sandbox\\src\\main\\resources" +
            "\\security";

    private static final WordTree WORD_TREE;

    static {
        //初始化字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }


    public static void main(String[] args) throws IOException {
        OldJavaNativeCodeSandbox oldJavaNativeCodeSandbox = new OldJavaNativeCodeSandbox();
        ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
        String code = ResourceUtil.readStr("testCode.unsafe/RunFileError.java", StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse = oldJavaNativeCodeSandbox.executeCode(executeCodeRequest);
        System.out.println("executeCodeResponse = " + executeCodeResponse);
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
//        System.setSecurityManager(new DenySecurityManager());

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        // 校验代码中，是否包含黑名单中的命令
//        FoundWord foundWord = WORD_TREE.matchWord(code);
//        if (foundWord != null) {
//            System.out.println("包含禁用词：" + foundWord.getFoundWord());
//            return null;
//        }

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
        // 4、编译代码，得到 class 文件
        String compileCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process compileProcess = Runtime.getRuntime().exec(compileCmd);
            ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(compileProcess, "编译");
            System.out.println("executeMessage = " + executeMessage);
        } catch (IOException e) {
            return getErrorResponse(e);
        }
        // 5、执行代码，得到输出结果
        ArrayList<ExecuteMessage> executeMessageList = new ArrayList<>();
        for (String inputArgs : inputList) {
            String runCmd = String.format("java -Xmx256m -Dfile.encoding=UTF-8 -cp %s;%s -Djava.security.manager=%s " +
                            "Main %s", userCodeParentPath,SECURITY_MANAGER_PATH,SECURITY_MANAGER_CLASSNAME, inputArgs);
            try {
                Process runProcess = Runtime.getRuntime().exec(runCmd);
                // 创建一个守护线程来监听执行代码的线程时间
//                new Thread(() -> {
//                    try {
//                        Thread.sleep(TIME_OUT);
//                        System.out.println("超时啦！！！");
//                        runProcess.destroy();
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
//                }).start();
                ExecuteMessage executeMessage = ProcessUtils.runProcessAndGetMessage(runProcess, "运行");
//                ExecuteMessage executeMessage = ProcessUtil.runInteractProcessAndGetMessage(runProcess, "1 2");
                System.out.println("executeMessage = " + executeMessage);
                executeMessageList.add(executeMessage);
            } catch (IOException e) {
                return getErrorResponse(e);
            }
        }

        // 6、整理输出
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
        // 文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }


    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        // 代码沙箱错误
        executeCodeResponse.setStatus(2);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
}

















