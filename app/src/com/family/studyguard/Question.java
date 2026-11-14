package com.family.studyguard;

/** 一道 4 选 1 的选择题。answer 为正确选项下标（0~3）。 */
public final class Question {
    public final String subject;
    public final String stem;
    public final String[] options;
    public final int answer;
    public final String explain;

    public Question(String subject, String stem, String[] options, int answer, String explain) {
        this.subject = subject;
        this.stem = stem;
        this.options = options;
        this.answer = answer;
        this.explain = explain;
    }

    public static final String[] LETTERS = {"A", "B", "C", "D"};
}
