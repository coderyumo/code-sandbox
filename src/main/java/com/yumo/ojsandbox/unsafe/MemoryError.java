package com.yumo.ojsandbox.unsafe;

import java.util.ArrayList;
import java.util.List;

public class MemoryError {

    public static void main(String[] args) throws InterruptedException {
        List<byte[]> bytes = new ArrayList<>();
        while (true) {
            bytes.add(new byte[10000]);
        }
    }
}
