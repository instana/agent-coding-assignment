package com.instana.agent;

public class AnnounceMessage {
    private final String pid;
    private final String javaVersion;

    public AnnounceMessage(String pid, String javaVersion) {
        this.pid = pid;
        this.javaVersion = javaVersion;
    }

    public String getPid() {
        return pid;
    }

    public String getJavaVersion() {
        return javaVersion;
    }
}
