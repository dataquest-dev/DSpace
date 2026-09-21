/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.startup.Tomcat;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Pins which byte sequences a servlet container accepts in a request target, for both URL forms of
 * {@link BitstreamByHandleRestController}, by driving an embedded Tomcat over a raw socket. A
 * reverse proxy that rewrites the request path percent-decodes it and re-escapes only its own small
 * set, so the path form reaches Tomcat the way these tests send it; the query string is proxied
 * verbatim.
 *
 * <p>MockMvc cannot stand in for this: it is handed an already-parsed path and never runs Tomcat's
 * request-line parser, so it is blind to this class of failure by construction.</p>
 */
public class BitstreamByHandleRequestTargetTest {

    private static final String BASE = "/api/core/bitstreams/handle/11858/00-test";

    private static Tomcat tomcat;
    private static int port;

    @BeforeClass
    public static void startContainer() throws Exception {
        tomcat = new Tomcat();
        tomcat.setSilent(true);
        tomcat.setBaseDir(Files.createTempDirectory("bbh-request-target").toString());
        tomcat.setPort(0);
        Context ctx = tomcat.addContext("", Files.createTempDirectory("bbh-docbase").toString());
        Tomcat.addServlet(ctx, "echo", new HttpServlet() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.setContentType("text/plain;charset=UTF-8");
                resp.getWriter().write(String.valueOf(req.getParameter("filename")));
            }
        });
        ctx.addServletMappingDecoded("/*", "echo");
        tomcat.getConnector();
        tomcat.start();
        port = tomcat.getConnector().getLocalPort();
    }

    @AfterClass
    public static void stopContainer() throws Exception {
        if (tomcat != null) {
            tomcat.stop();
            tomcat.destroy();
        }
    }

    @Test
    public void percentEncodedQuoteInPathSegmentIsAccepted() throws Exception {
        assertEquals(200, statusOf(BASE + "/file%20%22quoted%22.txt"));
    }

    @Test
    public void decodedQuoteInPathSegmentIsRejected() throws Exception {
        assertEquals(400, statusOf(BASE + "/file%20\"quoted\".txt"));
    }

    @Test
    public void encodedBackslashInPathSegmentIsRejected() throws Exception {
        assertEquals(400, statusOf(BASE + "/back%5Cslash.txt"));
    }

    @Test
    public void percentEncodedQuoteInFilenameParamIsAccepted() throws Exception {
        assertEquals(200, statusOf(BASE + "?filename=file%20%22quoted%22.txt"));
    }

    @Test
    public void percentEncodedBackslashInFilenameParamIsAccepted() throws Exception {
        assertEquals(200, statusOf(BASE + "?filename=back%5Cslash.txt"));
    }

    @Test
    public void decodedQuoteInFilenameParamIsRejected() throws Exception {
        assertEquals(400, statusOf(BASE + "?filename=file%20\"quoted\".txt"));
    }

    /** Send one request line verbatim and return the status code the container answers with. */
    private static int statusOf(String requestTarget) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write(("GET " + requestTarget + " HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.flush();
            return Integer.parseInt(readStatusLine(socket.getInputStream()).split(" ")[1]);
        }
    }

    private static String readStatusLine(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1 && c != '\n') {
            if (c != '\r') {
                line.append((char) c);
            }
        }
        return line.toString();
    }
}
