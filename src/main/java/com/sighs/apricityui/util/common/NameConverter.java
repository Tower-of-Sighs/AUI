package com.sighs.apricityui.util.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * 命名规范转换器
 * 目前支持小驼峰命名法、大驼峰命名法和蛇形命名法这些命名规范之间的相互转换
 */
public enum NameConverter {
    /**
     * 小驼峰命名法 - 首字母小写，后续单词首字母大写
     */
    CAMEL_CASE {
        @Override
        public String joinWords(List<String> words) {
            if (words.isEmpty()) {
                return "";
            }

            StringBuilder result = new StringBuilder(words.getFirst().toLowerCase(Locale.ROOT));
            for (int i = 1; i < words.size(); i++) {
                String word = words.get(i);
                if (!word.isEmpty()) {
                    result.append(Character.toUpperCase(word.charAt(0)))
                            .append(word.substring(1).toLowerCase(Locale.ROOT));
                }
            }
            return result.toString();
        }
    },

    /**
     * 大驼峰命名法 - 每个单词首字母大写
     */
    PASCAL_CASE {
        @Override
        public String joinWords(List<String> words) {
            StringBuilder result = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) {
                    result.append(Character.toUpperCase(word.charAt(0)))
                            .append(word.substring(1).toLowerCase(Locale.ROOT));
                }
            }
            return result.toString();
        }
    },

    /**
     * 蛇形命名法 - 单词之间用下划线分隔，全部小写
     */
    SNAKE_CASE {
        @Override
        public String joinWords(List<String> words) {
            return String.join("_", words).toLowerCase(Locale.ROOT);
        }
    };

    /**
     * 将单词列表按照当前命名规范连接成字符串
     *
     * @param words 单词列表
     * @return 按照当前规范连接的字符串
     */
    public abstract String joinWords(List<String> words);

    /**
     * 将名称拆分为单词列表
     *
     * @param name 输入名称
     * @return 单词列表
     */
    public static List<String> splitName(String name) {
        List<String> words = new ArrayList<>();

        if (name == null || name.isEmpty()) {
            return words;
        }

        // 处理蛇形命名法
        if (name.contains("_")) {
            for (String word : name.split("_")) {
                if (!word.isEmpty()) {
                    words.add(word.toLowerCase(Locale.ROOT));
                }
            }
            return words;
        }

        // 处理驼峰命名法 (小驼峰/大驼峰)
        StringBuilder currentWord = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);

            // 如果是大写字母且不是第一个字符，说明是新单词的开始
            if (Character.isUpperCase(c) && i > 0) {
                if (!currentWord.isEmpty()) {
                    words.add(currentWord.toString().toLowerCase(Locale.ROOT));
                    currentWord = new StringBuilder();
                }
            }

            currentWord.append(c);
        }

        // 添加最后一个单词
        if (!currentWord.isEmpty()) {
            words.add(currentWord.toString().toLowerCase(Locale.ROOT));
        }

        return words;
    }
}