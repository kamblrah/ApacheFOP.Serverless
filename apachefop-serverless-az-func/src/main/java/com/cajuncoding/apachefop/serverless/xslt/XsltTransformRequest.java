package com.cajuncoding.apachefop.serverless.xslt;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request model for XSLT transformation endpoint.
 * Accepts XML data and XSLT stylesheet with optional parameters.
 */
public class XsltTransformRequest {
    @JsonProperty("xml")
    private String xml;

    @JsonProperty("xslt")
    private String xslt;

    @JsonProperty("parameters")
    private Map<String, String> parameters;

    public XsltTransformRequest() {
    }

    public XsltTransformRequest(String xml, String xslt, Map<String, String> parameters) {
        this.xml = xml;
        this.xslt = xslt;
        this.parameters = parameters;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public String getXslt() {
        return xslt;
    }

    public void setXslt(String xslt) {
        this.xslt = xslt;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
}
