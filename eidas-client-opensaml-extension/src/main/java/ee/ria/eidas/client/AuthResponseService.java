package ee.ria.eidas.client;

import ee.ria.eidas.client.metadata.IDPMetadataResolver;
import ee.ria.eidas.client.response.AssertionValidator;
import ee.ria.eidas.client.config.EidasClientProperties;
import ee.ria.eidas.client.config.OpenSAMLConfiguration;
import ee.ria.eidas.client.exception.AuthenticationFailedException;
import ee.ria.eidas.client.exception.EidasClientException;
import ee.ria.eidas.client.exception.InvalidRequestException;
import ee.ria.eidas.client.response.AuthenticationResult;
import ee.ria.eidas.client.session.RequestSessionService;
import ee.ria.eidas.client.util.OpenSAMLUtils;
import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.net.URIComparator;
import net.shibboleth.utilities.java.support.net.URIException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.xml.XMLParserException;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.io.UnmarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.messaging.context.MessageContext;
import org.opensaml.messaging.handler.MessageHandler;
import org.opensaml.messaging.handler.MessageHandlerException;
import org.opensaml.messaging.handler.impl.BasicMessageHandlerChain;
import org.opensaml.messaging.handler.impl.SchemaValidateXMLMessage;
import org.opensaml.saml.common.binding.security.impl.MessageLifetimeSecurityHandler;
import org.opensaml.saml.common.binding.security.impl.ReceivedEndpointSecurityHandler;
import org.opensaml.saml.common.messaging.context.SAMLMessageInfoContext;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.criterion.EntityRoleCriterion;
import org.opensaml.saml.criterion.ProtocolCriterion;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.encryption.Decrypter;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.opensaml.saml.security.impl.SAMLSignatureProfileValidator;
import org.opensaml.security.credential.Credential;
import org.opensaml.security.credential.UsageType;
import org.opensaml.security.criteria.UsageCriterion;
import org.opensaml.xmlsec.encryption.support.DecryptionException;
import org.opensaml.xmlsec.encryption.support.InlineEncryptedKeyResolver;
import org.opensaml.xmlsec.keyinfo.impl.StaticKeyInfoCredentialResolver;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.opensaml.xmlsec.signature.support.SignatureValidator;
import org.opensaml.xmlsec.signature.support.impl.ExplicitKeySignatureTrustEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.xml.validation.Schema;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class AuthResponseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthResponseService.class);

    private RequestSessionService requestSessionService;

    private EidasClientProperties eidasClientProperties;

    private IDPMetadataResolver idpMetadataResolver;

    private Credential spAssertionDecryptionCredential;

    private Schema samlSchema;

    public AuthResponseService(RequestSessionService requestSessionService, EidasClientProperties eidasClientProperties, IDPMetadataResolver idpMetadataResolver, Credential spAssertionDecryptionCredential, Schema samlSchema) {
        this.requestSessionService = requestSessionService;
        this.eidasClientProperties = eidasClientProperties;
        this.idpMetadataResolver = idpMetadataResolver;
        this.spAssertionDecryptionCredential = spAssertionDecryptionCredential;
        this.samlSchema = samlSchema;
    }

    public AuthenticationResult getAuthenticationResult(HttpServletRequest req) {
        Response samlResponse;
        try {
            String encodedSamlResponse = req.getParameter("SAMLResponse");
            byte[] decodedSamlResponse = Base64.getDecoder().decode(encodedSamlResponse);
            String decodedSAMLstr = new String(decodedSamlResponse, StandardCharsets.UTF_8);
            samlResponse = getSamlResponse(decodedSAMLstr);
            LOGGER.info(OpenSAMLUtils.getXmlString(samlResponse));
        } catch (Exception e) {
            throw new InvalidRequestException("Failed to read SAMLResponse. " + e.getMessage(), e);
        }
        validateDestinationAndLifetime(samlResponse, req);
        validateStatusCode(samlResponse);

        EncryptedAssertion encryptedAssertion = getEncryptedAssertion(samlResponse);
        Assertion assertion = decryptAssertion(encryptedAssertion);
        verifyAssertionSignature(assertion);
        validateAssertion(assertion);
        LOGGER.debug("Decrypted Assertion: {}", OpenSAMLUtils.getXmlString(assertion));

        return new AuthenticationResult(assertion);
    }

    private void validateStatusCode(Response samlResponse) {
        StatusCode statusCode = samlResponse.getStatus().getStatusCode();
        StatusCode substatusCode = statusCode.getStatusCode();
        StatusMessage statusMessage = samlResponse.getStatus().getStatusMessage();
        if (StatusCode.SUCCESS.equals(statusCode.getValue())) {
            return;
        }  else if (isStatusNoConsentGiven(statusCode, substatusCode, StatusCode.REQUESTER, StatusCode.REQUEST_DENIED)) {
            throw new AuthenticationFailedException("No user consent received. User denied access.");
        }  else if (isStatusAuthenticationFailed(statusCode, substatusCode, StatusCode.RESPONDER, StatusCode.AUTHN_FAILED)) {
            throw new AuthenticationFailedException("Authentication failed.");
        } else {
            throw new EidasClientException("Eidas node responded with an error! statusCode = " + samlResponse.getStatus().getStatusCode().getValue()
                    + (substatusCode != null ? ", substatusCode = " + substatusCode.getValue() : "")
                    +  ", statusMessage = " + statusMessage.getMessage());
        }
    }

    private boolean isStatusAuthenticationFailed(StatusCode statusCode, StatusCode substatusCode, String responder, String authnFailed) {
        return responder.equals(statusCode.getValue())
                && (substatusCode != null && authnFailed.equals(substatusCode.getValue()));
    }

    private boolean isStatusNoConsentGiven(StatusCode statusCode, StatusCode substatusCode, String requester, String requestDenied) {
        return requester.equals(statusCode.getValue())
                && (substatusCode != null && requestDenied.equals(substatusCode.getValue()));
    }

    private Response getSamlResponse(String samlResponse) throws XMLParserException, UnmarshallingException {
        return (Response) XMLObjectSupport.unmarshallFromInputStream(
                OpenSAMLConfiguration.getParserPool(), new ByteArrayInputStream(samlResponse.getBytes(StandardCharsets.UTF_8)));
    }

    private void validateDestinationAndLifetime(Response samlResponse, HttpServletRequest request) {
        MessageContext context = new MessageContext<Response>();
        context.setMessage(samlResponse);
        SAMLMessageInfoContext messageInfoContext = context.getSubcontext(SAMLMessageInfoContext.class, true);
        messageInfoContext.setMessageIssueInstant(samlResponse.getIssueInstant());

        SchemaValidateXMLMessage schemaValidationFilter = new SchemaValidateXMLMessage(samlSchema);

        MessageLifetimeSecurityHandler lifetimeSecurityHandler = new MessageLifetimeSecurityHandler();
        lifetimeSecurityHandler.setClockSkew(eidasClientProperties.getAcceptedClockSkew() * 1000L);
        lifetimeSecurityHandler.setMessageLifetime(eidasClientProperties.getResponseMessageLifeTime() * 1000L);
        lifetimeSecurityHandler.setRequiredRule(true);

        ReceivedEndpointSecurityHandler receivedEndpointSecurityHandler = new ReceivedEndpointSecurityHandler();
        receivedEndpointSecurityHandler.setHttpServletRequest(request);
        List handlers = new ArrayList<MessageHandler>();

        handlers.add(schemaValidationFilter);
        handlers.add(lifetimeSecurityHandler);
        handlers.add(receivedEndpointSecurityHandler);
        receivedEndpointSecurityHandler.setURIComparator(new URIComparator() {
            @Override
            public boolean compare(@Nullable String messageDestination, @Nullable String receiverEndpoint) throws URIException {
                return messageDestination!= null && receiverEndpoint != null && messageDestination.equals(eidasClientProperties.getCallbackUrl());
            }
        });

        BasicMessageHandlerChain<ArtifactResponse> handlerChain = new BasicMessageHandlerChain<>();
        handlerChain.setHandlers(handlers);

        try {
            handlerChain.initialize();
            handlerChain.doInvoke(context);
        } catch (ComponentInitializationException e) {
            throw new EidasClientException("Error initializing handler chain", e);
        } catch (MessageHandlerException e) {
            throw new InvalidRequestException("Error handling message: " + e.getMessage(), e);
        }

    }

    private void validateAssertion(Assertion assertion) {
        AssertionValidator assertionValidator = new AssertionValidator(eidasClientProperties, requestSessionService);
        assertionValidator.validate(assertion);
    }

    private Assertion decryptAssertion(EncryptedAssertion encryptedAssertion) {
        StaticKeyInfoCredentialResolver keyInfoCredentialResolver = new StaticKeyInfoCredentialResolver(spAssertionDecryptionCredential);

        Decrypter decrypter = new Decrypter(null, keyInfoCredentialResolver, new InlineEncryptedKeyResolver());
        decrypter.setRootInNewDocument(true);

        try {
            return decrypter.decrypt(encryptedAssertion);
        } catch (DecryptionException e) {
            throw new EidasClientException("Error decrypting assertion", e);
        }
    }

    private void verifyAssertionSignature(Assertion assertion) {
        if (!assertion.isSigned()) {
            throw new InvalidRequestException("The SAML Assertion was not signed");
        }
        try {
            SAMLSignatureProfileValidator profileValidator = new SAMLSignatureProfileValidator();
            profileValidator.validate(assertion.getSignature());

            final CriteriaSet criteriaSet = new CriteriaSet();
            criteriaSet.add(new UsageCriterion(UsageType.SIGNING));
            criteriaSet.add(new EntityRoleCriterion(IDPSSODescriptor.DEFAULT_ELEMENT_NAME));
            criteriaSet.add(new ProtocolCriterion(SAMLConstants.SAML20P_NS));
            criteriaSet.add(new EntityIdCriterion(eidasClientProperties.getIdpMetadataUrl()));
            Credential credential = idpMetadataResolver.responseSignatureTrustEngine().getCredentialResolver().resolveSingle(criteriaSet);
            SignatureValidator.validate(assertion.getSignature(), credential);

            LOGGER.info("SAML Assertion signature verified");
        } catch (SignatureException | ResolverException e) {
            throw new EidasClientException("Signature verification failed!", e);
        }
    }

    private EncryptedAssertion getEncryptedAssertion(Response samlResponse) {
        List<EncryptedAssertion> response = samlResponse.getEncryptedAssertions();
        if (response == null || response.isEmpty()) {
            throw new EidasClientException("Saml Response does not contain any encrypted assertions");
        } else if (response.size() > 1) {
            throw new EidasClientException("Saml Response contains more than 1 encrypted assertion");
        }
        return response.get(0);
    }
}
