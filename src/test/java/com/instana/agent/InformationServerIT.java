package com.instana.agent;

import org.junit.*;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static org.awaitility.Awaitility.await;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.exactly;

// !! Make sure "mvn package" runs before executing tests !! //
public class InformationServerIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(InformationServerIT.class);
    private static final int LISTEN_PORT = 8080;

    private static ClientAndServer mockServer;

    public GenericContainer<?> appContainer = new GenericContainer<>("adoptopenjdk/openjdk11:debianslim-slim");

    @BeforeClass
    public static void startServer() {
        mockServer = startClientAndServer(LISTEN_PORT);
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServer);
    }

    @Before
    public void setUp() throws SocketException {
        // Might be necessary to change the IP to "host.docker.internal" for Docker on Mac
        String publicIP = findPublicIP();
        LOGGER.debug("Found IP for listening: " + publicIP);

        // Run Docker container with the "InformationClient" inside
        MountableFile informationClientFile = MountableFile.forHostPath(
                Paths.get(".", "target", "agent-coding-assignment-1.0-SNAPSHOT-shaded.jar"));
        appContainer.withCopyFileToContainer(informationClientFile, "/information-client.jar");
        appContainer.setCommand("java", "-jar", "/information-client.jar");

        Slf4jLogConsumer logConsumer = new Slf4jLogConsumer(LOGGER).withSeparateOutputStreams();
        appContainer.withLogConsumer(logConsumer);

        appContainer.addEnv("INSTANA_SERVER_HOST", publicIP);
        appContainer.addEnv("INSTANA_SERVER_PORT", String.valueOf(LISTEN_PORT));
    }

    @After
    public void tearDown() {
        appContainer.stop(); // Make sure the container is always stopped
        mockServer.reset();
    }

    @Test
    public void testAppStartup() {
        mockServer
                .when(request()
                        .withMethod("POST")
                        .withPath("/"))
                .respond(response()
                        .withHeaders(new Header(CONTENT_TYPE.toString(), "application/json"))
                        .withBody(""));

        appContainer.start();

        awaitServer(10, TimeUnit.SECONDS, () ->
                mockServer.verify(request()
                                .withMethod("POST")
                                .withPath("/"),
                        exactly(1)
                ));
    }

    @Test
    public void testClientAnnouncesCorrectly() {
        mockServer
                .when(request()
                        .withMethod("POST")
                        .withPath("/"))
                .respond(response()
                        .withHeaders(new Header(CONTENT_TYPE.toString(), "application/json"))
                        .withBody(""));

        appContainer.start();

        awaitServer(10, TimeUnit.SECONDS, () ->
                mockServer.verify(request()
                                .withMethod("POST")
                                .withPath("/")
                                .withBody(json("{" + NEW_LINE +
                                                "    \"pid\": \"${json-unit.any-string}\"," + NEW_LINE +
                                                "    \"java_version\": \"${json-unit.any-string}\"," + NEW_LINE +
                                                "}",
                                        MatchType.ONLY_MATCHING_FIELDS
                                )),
                        exactly(1)
                ));
    }

    @Test
    public void testClientRespondsWithRequestedInfo() {
        mockServer
                .when(request()
                        .withMethod("POST")
                        .withPath("/"))
                .respond(response()
                        .withHeaders(new Header(CONTENT_TYPE.toString(), "application/json"))
                        .withBody("" +
                                "{" + NEW_LINE +
                                "    \"requested\": \"processes\"," + NEW_LINE +
                                "}"));

        appContainer.start();

        awaitServer(15, TimeUnit.SECONDS, () ->
                mockServer.verify(
                        request()
                                .withMethod("POST")
                                .withPath("/")
                                .withBody(json("{" + NEW_LINE +
                                                "    \"pid\": \"${json-unit.any-string}\"," + NEW_LINE +
                                                "    \"java_version\": \"${json-unit.any-string}\"," + NEW_LINE +
                                                "}",
                                        MatchType.ONLY_MATCHING_FIELDS
                                )),
                        request()
                                .withMethod("POST")
                                .withPath("/")
                                .withBody(json("{" + NEW_LINE +
                                                        "    \"processes\": \"${json-unit.any-list}\"," + NEW_LINE +
                                                        "}",
                                                MatchType.ONLY_MATCHING_FIELDS
                                        )
                                )
                ));
    }

    private static String findPublicIP() throws SocketException {
        String publicIP = "127.0.0.1"; // Fall-back to loopback address if we cannot find a better (public) IP

        Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
        while (networkInterfaces.hasMoreElements()) {
            NetworkInterface networkInterface = networkInterfaces.nextElement();

            if (!expectedLocalInterfaceName(networkInterface.getName())) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                if (addr instanceof Inet4Address) {
                    publicIP = addr.getHostAddress();
                }
            }
        }

        return publicIP;
    }

    private static boolean expectedLocalInterfaceName(String name) {
        return name.startsWith("en") || name.startsWith("eth") || name.startsWith("wlan") || name.startsWith("wlp");
    }

    private static void awaitServer(long timeout, TimeUnit unit, Runnable verify) {
        await().atMost(timeout, unit)
                .pollInterval(3, TimeUnit.SECONDS)
                .until(() -> {
                            try {
                                verify.run();
                                return true;
                            } catch (Throwable e) {
                                return false;
                            }
                        }
                );
    }
}
