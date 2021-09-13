package com.instana.agent;

import java.util.regex.Pattern;

public class DockerInformation {

    // https://github.com/docker/docker/blob/master/image/image.go#L12
    public static final Pattern DOCKER_CONTAINER_ID_PATTERN = Pattern.compile("([a-f0-9]{64})");

}
