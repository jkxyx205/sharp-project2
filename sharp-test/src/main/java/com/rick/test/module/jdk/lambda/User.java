package com.rick.test.module.jdk.lambda;

public class User {
    private String name;

    public int age() {
        return 0;
    }


    /**
     * 必须静态方法 User::score
     *
     * @param score
     * @return
     */
    public static int score(int score) {
        return score;
    }

    public static int score2() {
        return 1;
    }

    /**
     * 可以通过对象实例::print
     *
     * @param s
     * @return
     */
    public String print(String s) {
        int a = 0;
        return s;
    }

    public void handler(int type) {

    }

    public User() {
    }

    public User(String name) {
        this.name = name;
    }

    public String getName() {   // 实例方法（非 static）
        return name;
    }

    public void setAge() {

    }


}