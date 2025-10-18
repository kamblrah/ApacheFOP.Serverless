package com.cajuncoding;

import com.cajuncoding.apachefop.serverless.ApacheFopXsltFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for XSLT transformation endpoint.
 */
public class XsltFunctionTest {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    // Sample XML data
    private static final String SAMPLE_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<document>\n" +
            "  <title>Test Document</title>\n" +
            "  <content>This is a test.</content>\n" +
            "</document>";

    // Sample XSLT that transforms to XSL-FO
    private static final String SAMPLE_XSLT = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<xsl:stylesheet version=\"1.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">\n" +
            "  <xsl:output method=\"xml\" version=\"1.0\" indent=\"yes\"/>\n" +
            "  <xsl:template match=\"/\">\n" +
            "    <fo:root>\n" +
            "      <fo:layout-master-set>\n" +
            "        <fo:simple-page-master master-name=\"A4\" page-width=\"210mm\" page-height=\"297mm\" margin=\"20mm\">\n" +
            "          <fo:region-body/>\n" +
            "        </fo:simple-page-master>\n" +
            "      </fo:layout-master-set>\n" +
            "      <fo:page-sequence master-reference=\"A4\">\n" +
            "        <fo:flow flow-name=\"xsl-region-body\">\n" +
            "          <fo:block font-size=\"16pt\" font-weight=\"bold\">\n" +
            "            <xsl:value-of select=\"document/title\"/>\n" +
            "          </fo:block>\n" +
            "          <fo:block margin-top=\"10pt\">\n" +
            "            <xsl:value-of select=\"document/content\"/>\n" +
            "          </fo:block>\n" +
            "        </fo:flow>\n" +
            "      </fo:page-sequence>\n" +
            "    </fo:root>\n" +
            "  </xsl:template>\n" +
            "</xsl:stylesheet>";

    private ExecutionContext context;
    private Logger logger;

    @BeforeEach
    public void setUp() {
        context = mock(ExecutionContext.class);
        logger = Logger.getGlobal();
        when(context.getLogger()).thenReturn(logger);
    }

    @Test
    public void testValidXsltTransformation() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        // Create JSON request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("xml", SAMPLE_XML);
        requestBody.put("xslt", SAMPLE_XSLT);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        when(req.getBody()).thenReturn(Optional.of(jsonBody));

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.OK, ret.getStatus());
        
        // Verify the response body is a byte array (PDF)
        Object body = ret.getBody();
        assertNotNull(body);
        assertTrue(body instanceof byte[], "Response body should be a byte array (PDF)");
        
        byte[] pdfBytes = (byte[]) body;
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        
        // Verify PDF header (starts with %PDF)
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, Math.min(4, pdfBytes.length)));
        assertEquals("%PDF", pdfHeader, "Response should be a valid PDF");
    }

    @Test
    public void testMissingRequestBody() {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        when(req.getBody()).thenReturn(Optional.empty());

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.BAD_REQUEST, ret.getStatus());
        
        // Verify error message
        String body = (String) ret.getBody();
        assertNotNull(body);
        assertTrue(body.contains("MissingBody") || body.contains("error"));
    }

    @Test
    public void testInvalidJson() {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        when(req.getBody()).thenReturn(Optional.of("{ invalid json "));

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.BAD_REQUEST, ret.getStatus());
        
        String body = (String) ret.getBody();
        assertNotNull(body);
        assertTrue(body.contains("InvalidJson") || body.contains("error"));
    }

    @Test
    public void testMissingXml() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        // Missing XML
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("xslt", SAMPLE_XSLT);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        when(req.getBody()).thenReturn(Optional.of(jsonBody));

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.BAD_REQUEST, ret.getStatus());
        
        String body = (String) ret.getBody();
        assertNotNull(body);
        assertTrue(body.contains("MissingXml") || body.contains("error"));
    }

    @Test
    public void testMissingXslt() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        // Missing XSLT
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("xml", SAMPLE_XML);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        when(req.getBody()).thenReturn(Optional.of(jsonBody));

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.BAD_REQUEST, ret.getStatus());
        
        String body = (String) ret.getBody();
        assertNotNull(body);
        assertTrue(body.contains("MissingXslt") || body.contains("error"));
    }

    @Test
    public void testInvalidXslt() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        // Invalid XSLT (not proper XML)
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("xml", SAMPLE_XML);
        requestBody.put("xslt", "This is not valid XSLT");
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        when(req.getBody()).thenReturn(Optional.of(jsonBody));

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.BAD_REQUEST, ret.getStatus());
        
        String body = (String) ret.getBody();
        assertNotNull(body);
        assertTrue(body.contains("InvalidXslt") || body.contains("error"));
    }

    @Test
    public void testXsltWithParameters() throws Exception {
        // Setup
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        // Create JSON request body with parameters
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("xml", SAMPLE_XML);
        requestBody.put("xslt", SAMPLE_XSLT);
        
        Map<String, String> params = new HashMap<>();
        params.put("testParam", "testValue");
        requestBody.put("parameters", params);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        when(req.getBody()).thenReturn(Optional.of(jsonBody));

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.OK, ret.getStatus());
        
        // Verify the response body is a byte array (PDF)
        Object body = ret.getBody();
        assertNotNull(body);
        assertTrue(body instanceof byte[], "Response body should be a byte array (PDF)");
    }

    @Test
    public void testXslt20Features() throws Exception {
        // Setup - Test XSLT 2.0 specific features with Saxon
        @SuppressWarnings("unchecked")
        final HttpRequestMessage<Optional<String>> req = mock(HttpRequestMessage.class);

        Map<String, String> queryParams = new HashMap<>();
        when(req.getQueryParameters()).thenReturn(queryParams);

        Map<String, String> headers = new HashMap<>();
        when(req.getHeaders()).thenReturn(headers);

        // Sample XML with multiple items
        String xmlWith2_0Features = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<catalog>\n" +
                "  <item category=\"book\"><title>Book 1</title></item>\n" +
                "  <item category=\"book\"><title>Book 2</title></item>\n" +
                "  <item category=\"dvd\"><title>DVD 1</title></item>\n" +
                "</catalog>";

        // XSLT 2.0 stylesheet using for-each-group (not supported in XSLT 1.0)
        String xslt20Stylesheet = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">\n" +
                "  <xsl:output method=\"xml\" version=\"1.0\" indent=\"yes\"/>\n" +
                "  <xsl:template match=\"/\">\n" +
                "    <fo:root>\n" +
                "      <fo:layout-master-set>\n" +
                "        <fo:simple-page-master master-name=\"A4\" page-width=\"210mm\" page-height=\"297mm\" margin=\"20mm\">\n" +
                "          <fo:region-body/>\n" +
                "        </fo:simple-page-master>\n" +
                "      </fo:layout-master-set>\n" +
                "      <fo:page-sequence master-reference=\"A4\">\n" +
                "        <fo:flow flow-name=\"xsl-region-body\">\n" +
                "          <fo:block font-size=\"16pt\" font-weight=\"bold\">Catalog Items (Grouped by Category)</fo:block>\n" +
                "          <xsl:for-each-group select=\"catalog/item\" group-by=\"@category\">\n" +
                "            <fo:block margin-top=\"10pt\" font-weight=\"bold\">\n" +
                "              Category: <xsl:value-of select=\"current-grouping-key()\"/>\n" +
                "            </fo:block>\n" +
                "            <xsl:for-each select=\"current-group()\">\n" +
                "              <fo:block margin-left=\"10pt\">\n" +
                "                - <xsl:value-of select=\"title\"/>\n" +
                "              </fo:block>\n" +
                "            </xsl:for-each>\n" +
                "          </xsl:for-each-group>\n" +
                "        </fo:flow>\n" +
                "      </fo:page-sequence>\n" +
                "    </fo:root>\n" +
                "  </xsl:template>\n" +
                "</xsl:stylesheet>";
        
        // Create JSON request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("xml", xmlWith2_0Features);
        requestBody.put("xslt", xslt20Stylesheet);
        
        String jsonBody = objectMapper.writeValueAsString(requestBody);
        when(req.getBody()).thenReturn(Optional.of(jsonBody));

        doAnswer(new Answer<HttpResponseMessage.Builder>() {
            @Override
            public HttpResponseMessage.Builder answer(InvocationOnMock invocation) {
                HttpStatus status = (HttpStatus) invocation.getArguments()[0];
                return new HttpResponseMessageMock.HttpResponseMessageBuilderMock().status(status);
            }
        }).when(req).createResponseBuilder(any(HttpStatus.class));

        // Invoke
        final HttpResponseMessage ret = new ApacheFopXsltFunction().run(req, context);

        // Verify
        assertNotNull(ret);
        assertEquals(HttpStatus.OK, ret.getStatus(), "XSLT 2.0 transformation should succeed with Saxon-HE");
        
        // Verify the response body is a byte array (PDF)
        Object body = ret.getBody();
        assertNotNull(body);
        assertTrue(body instanceof byte[], "Response body should be a byte array (PDF)");
        
        byte[] pdfBytes = (byte[]) body;
        assertTrue(pdfBytes.length > 0, "PDF should not be empty");
        
        // Verify PDF header (starts with %PDF)
        String pdfHeader = new String(Arrays.copyOfRange(pdfBytes, 0, Math.min(4, pdfBytes.length)));
        assertEquals("%PDF", pdfHeader, "Response should be a valid PDF generated from XSLT 2.0 stylesheet");
    }
}
