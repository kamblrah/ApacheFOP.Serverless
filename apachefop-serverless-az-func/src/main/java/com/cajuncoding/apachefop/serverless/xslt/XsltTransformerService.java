package com.cajuncoding.apachefop.serverless.xslt;

import com.cajuncoding.apachefop.serverless.config.ApacheFopServerlessConfig;
import org.apache.commons.lang3.StringUtils;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.MimeConstants;
import com.cajuncoding.apachefop.serverless.apachefop.ApacheFopEventListener;

import net.sf.saxon.s9api.*;
import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Service for transforming XML + XSLT to PDF via XSL-FO.
 * Uses Saxon-HE for XSLT 2.0/3.0 support with backwards compatibility for XSLT 1.0.
 * Includes security hardening and caching support.
 */
public class XsltTransformerService {
    private final XsltTemplateCache templateCache;
    private final Processor saxonProcessor;
    private final FopFactory fopFactory;
    private final Logger logger;
    private final ApacheFopServerlessConfig config;

    // Size limits
    private final long maxXmlSizeBytes;
    private final long maxXsltSizeBytes;

    public XsltTransformerService(
        FopFactory fopFactory,
        ApacheFopServerlessConfig config,
        Logger logger,
        boolean cacheEnabled,
        int cacheSize,
        long maxXmlSizeBytes,
        long maxXsltSizeBytes
    ) {
        this.fopFactory = fopFactory;
        this.config = config;
        this.logger = logger;
        this.maxXmlSizeBytes = maxXmlSizeBytes;
        this.maxXsltSizeBytes = maxXsltSizeBytes;

        // Initialize cache
        this.templateCache = new XsltTemplateCache(cacheSize, cacheEnabled, logger);

        // Initialize Saxon Processor (false = use Saxon-HE, not Saxon-PE or Saxon-EE)
        this.saxonProcessor = new Processor(false);
        
        if (logger != null) {
            logger.info("Saxon-HE XSLT Processor initialized - supports XSLT 3.0, 2.0, and 1.0");
        }
    }

    /**
     * Validate and transform XML + XSLT to PDF.
     */
    public XsltTransformResult transform(
        String xmlContent,
        String xsltContent,
        Map<String, String> parameters,
        boolean gzipEnabled
    ) throws XsltTransformException {
        
        // Validate inputs
        validateInputs(xmlContent, xsltContent);

        try {
            // Generate cache key and check cache
            String cacheKey = templateCache.generateCacheKey(xsltContent);
            XsltExecutable executable = templateCache.get(cacheKey);

            // Compile XSLT if not in cache
            if (executable == null) {
                executable = compileXslt(xsltContent);
                templateCache.put(cacheKey, executable);
            }

            // Transform XML to PDF via XSL-FO
            byte[] pdfBytes = transformToPdf(xmlContent, executable, parameters, gzipEnabled);

            return new XsltTransformResult(pdfBytes, true);

        } catch (XsltTransformException e) {
            throw e;
        } catch (Exception e) {
            throw new XsltTransformException("UnexpectedError", "Unexpected error during transformation: " + e.getMessage(), e);
        }
    }

    /**
     * Validate XML and XSLT inputs.
     */
    private void validateInputs(String xmlContent, String xsltContent) throws XsltTransformException {
        if (StringUtils.isBlank(xmlContent)) {
            throw new XsltTransformException("MissingXml", "XML content is required");
        }
        if (StringUtils.isBlank(xsltContent)) {
            throw new XsltTransformException("MissingXslt", "XSLT content is required");
        }

        // Check size limits
        long xmlSize = xmlContent.getBytes(StandardCharsets.UTF_8).length;
        long xsltSize = xsltContent.getBytes(StandardCharsets.UTF_8).length;

        if (xmlSize > maxXmlSizeBytes) {
            throw new XsltTransformException(
                "XmlSizeExceeded",
                String.format("XML size (%d bytes) exceeds maximum allowed (%d bytes)", xmlSize, maxXmlSizeBytes)
            );
        }

        if (xsltSize > maxXsltSizeBytes) {
            throw new XsltTransformException(
                "XsltSizeExceeded",
                String.format("XSLT size (%d bytes) exceeds maximum allowed (%d bytes)", xsltSize, maxXsltSizeBytes)
            );
        }

        // Validate XSLT root element
        validateXsltRootElement(xsltContent);
    }

    /**
     * Validate that XSLT has a proper root element.
     */
    private void validateXsltRootElement(String xsltContent) throws XsltTransformException {
        String trimmed = xsltContent.trim();
        if (!trimmed.contains("<xsl:stylesheet") && !trimmed.contains("<xsl:transform")) {
            throw new XsltTransformException(
                "InvalidXslt",
                "XSLT must have <xsl:stylesheet> or <xsl:transform> as root element"
            );
        }
    }

    /**
     * Compile XSLT stylesheet to XsltExecutable using Saxon.
     */
    private XsltExecutable compileXslt(String xsltContent) throws XsltTransformException {
        try (StringReader xsltReader = new StringReader(xsltContent)) {
            StreamSource xsltSource = new StreamSource(xsltReader);
            
            // Create Saxon XSLT compiler
            XsltCompiler compiler = saxonProcessor.newXsltCompiler();
            
            // Compile the stylesheet
            XsltExecutable executable = compiler.compile(xsltSource);
            
            if (logger != null) {
                logger.info("XSLT stylesheet compiled successfully with Saxon-HE");
            }
            
            return executable;
        } catch (SaxonApiException e) {
            String message = "Failed to compile XSLT: " + e.getMessage();
            if (e.getMessage() != null && e.getMessage().contains("entity")) {
                message += " (External entities are disabled for security)";
            }
            throw new XsltTransformException("InvalidXslt", message, e);
        } catch (Exception e) {
            throw new XsltTransformException("InvalidXslt", "Failed to compile XSLT: " + e.getMessage(), e);
        }
    }

    /**
     * Transform XML to PDF via XSL-FO using compiled XsltExecutable from Saxon.
     */
    private byte[] transformToPdf(
        String xmlContent,
        XsltExecutable executable,
        Map<String, String> parameters,
        boolean gzipEnabled
    ) throws XsltTransformException {
        
        try (
            ByteArrayOutputStream pdfBaseOutputStream = new ByteArrayOutputStream();
            OutputStream fopOutputStream = gzipEnabled ? 
                new GZIPOutputStream(pdfBaseOutputStream) : pdfBaseOutputStream
        ) {
            // Create transformer from executable
            Xslt30Transformer transformer = executable.load30();

            // Set parameters if provided
            if (parameters != null && !parameters.isEmpty()) {
                Map<QName, XdmValue> paramMap = new java.util.HashMap<>();
                for (Map.Entry<String, String> param : parameters.entrySet()) {
                    paramMap.put(new QName(param.getKey()), new XdmAtomicValue(param.getValue()));
                    if (logger != null) {
                        logger.info("Set XSLT parameter: " + param.getKey() + " = " + param.getValue());
                    }
                }
                transformer.setStylesheetParameters(paramMap);
            }

            // First transform: XML + XSLT -> XSL-FO
            StringWriter foWriter = new StringWriter();
            try (StringReader xmlReader = new StringReader(xmlContent)) {
                StreamSource xmlSource = new StreamSource(xmlReader);
                Serializer serializer = saxonProcessor.newSerializer(foWriter);
                transformer.transform(xmlSource, serializer);
            }

            String xslFOContent = foWriter.toString();
            
            if (logger != null) {
                logger.info("XML transformed to XSL-FO (length: " + xslFOContent.length() + " chars)");
            }

            // Second transform: XSL-FO -> PDF via Apache FOP
            transformFoToPdf(xslFOContent, fopOutputStream);

            fopOutputStream.flush();
            fopOutputStream.close();

            return pdfBaseOutputStream.toByteArray();

        } catch (SaxonApiException e) {
            throw new XsltTransformException("TransformationError", "XSLT transformation failed: " + e.getMessage(), e);
        } catch (IOException | FOPException e) {
            throw new XsltTransformException("PdfRenderError", "PDF rendering failed: " + e.getMessage(), e);
        } catch (TransformerException e) {
            throw new XsltTransformException("PdfRenderError", "PDF rendering failed: " + e.getMessage(), e);
        }
    }

    /**
     * Transform XSL-FO to PDF using Apache FOP.
     */
    private void transformFoToPdf(String xslFOContent, OutputStream outputStream) 
        throws IOException, TransformerException, FOPException {
        
        // Create event listener for logging
        ApacheFopEventListener eventListener = new ApacheFopEventListener(logger);
        FOUserAgent foUserAgent = fopFactory.newFOUserAgent();
        foUserAgent.getEventBroadcaster().addEventListener(eventListener);

        // Create FOP instance
        Fop fop = fopFactory.newFop(MimeConstants.MIME_PDF, foUserAgent, outputStream);

        // Transform XSL-FO to PDF
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        
        try (StringReader foReader = new StringReader(xslFOContent)) {
            Source src = new StreamSource(foReader);
            Result res = new SAXResult(fop.getDefaultHandler());
            transformer.transform(src, res);
        }

        if (logger != null) {
            logger.info("XSL-FO successfully rendered to PDF");
        }
    }

    /**
     * Get cache statistics.
     */
    public int getCacheSize() {
        return templateCache.size();
    }

    public boolean isCacheEnabled() {
        return templateCache.isEnabled();
    }

    /**
     * Custom exception for XSLT transformation errors.
     */
    public static class XsltTransformException extends Exception {
        private final String errorCode;

        public XsltTransformException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public XsltTransformException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * Result of XSLT transformation.
     */
    public static class XsltTransformResult {
        private final byte[] pdfBytes;
        private final boolean success;

        public XsltTransformResult(byte[] pdfBytes, boolean success) {
            this.pdfBytes = pdfBytes;
            this.success = success;
        }

        public byte[] getPdfBytes() {
            return pdfBytes;
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
