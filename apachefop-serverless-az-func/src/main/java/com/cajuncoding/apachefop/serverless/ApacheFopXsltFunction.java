package com.cajuncoding.apachefop.serverless;

import com.cajuncoding.apachefop.serverless.web.ApacheFopServerlessFunctionExecutor;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

/**
 * Azure Function for XSLT transformation endpoint.
 * Accepts XML + XSLT and transforms to PDF via XSL-FO.
 */
public class ApacheFopXsltFunction {
    /**
     * This function listens at endpoint "/api/apache-fop/xslt".
     */
    @FunctionName("ApacheFOPXslt")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", route="apache-fop/xslt", methods = {HttpMethod.POST}, authLevel = AuthorizationLevel.FUNCTION)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        try {
            var functionExecutor = new ApacheFopServerlessFunctionExecutor();
            return functionExecutor.ExecuteXsltRequest(request, context.getLogger());
        }
        catch (Exception ex) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body(ex).build();
        }
    }
}
