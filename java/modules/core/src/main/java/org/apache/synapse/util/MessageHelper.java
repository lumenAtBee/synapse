package org.apache.synapse.util;

import org.apache.axiom.attachments.Attachments;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMNamespace;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.soap.*;
import org.apache.axiom.util.UIDGenerator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.addressing.AddressingConstants;
import org.apache.axis2.client.Options;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.neethi.Policy;
import org.apache.neethi.PolicyEngine;
import org.apache.synapse.FaultHandler;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.eip.EIPConstants;

import java.util.*;

/**
 *
 */
public class MessageHelper {


    private static Log log = LogFactory.getLog(MessageHelper.class);

    /**
     * This method will simulate cloning the message context and creating an exact copy of the
     * passed message. One should use this method with care; that is because, inside the new MC,
     * most of the attributes of the MC like opCtx and so on are still kept as references inside
     * the axis2 MessageContext for performance improvements. (Note: U dont have to worrie
     * about the SOAPEnvelope, it is a cloned copy and not a reference from any other MC)
     *
     * @param synCtx - this will be cloned 
     * @return cloned Synapse MessageContext
     * @throws AxisFault if there is a failure in creating the new Synapse MC or in a failure in
     *          clonning the underlying axis2 MessageContext
     * 
     * @see MessageHelper#cloneAxis2MessageContext 
     */
    public static MessageContext cloneMessageContext(MessageContext synCtx) throws AxisFault {

        // creates the new MessageContext and clone the internal axis2 MessageContext
        // inside the synapse message context and place that in the new one
        MessageContext newCtx = synCtx.getEnvironment().createMessageContext();
        Axis2MessageContext axis2MC = (Axis2MessageContext) newCtx;
        axis2MC.setAxis2MessageContext(
            cloneAxis2MessageContext(((Axis2MessageContext) synCtx).getAxis2MessageContext()));

        newCtx.setConfiguration(synCtx.getConfiguration());
        newCtx.setEnvironment(synCtx.getEnvironment());
        newCtx.setContextEntries(synCtx.getContextEntries());

        // set the parent correlation details to the cloned MC -
        //                              for the use of aggregation like tasks
        newCtx.setProperty(EIPConstants.AGGREGATE_CORRELATION, synCtx.getMessageID());

        // copying the core parameters of the synapse MC
        newCtx.setTo(synCtx.getTo());
        newCtx.setReplyTo(synCtx.getReplyTo());
        newCtx.setSoapAction(synCtx.getSoapAction());
        newCtx.setWSAAction(synCtx.getWSAAction());
        newCtx.setResponse(synCtx.isResponse());

        // copy all the synapse level properties to the newCtx
        for (Object o : synCtx.getPropertyKeySet()) {
            // If there are non String keyed properties neglect them rather than trow exception
            if (o instanceof String) {
                newCtx.setProperty((String) o, synCtx.getProperty((String) o));
            }
        }
        
        // Make deep copy of fault stack so that parent will not be lost it's fault stack
        Stack<FaultHandler> faultStack = synCtx.getFaultStack();
        if (!faultStack.isEmpty()) {
            
            List<FaultHandler> newFaultStack = new ArrayList<FaultHandler>();
            newFaultStack.addAll(faultStack);
            
            for (FaultHandler faultHandler : newFaultStack) {
                if (faultHandler != null) {
                    newCtx.pushFaultHandler(faultHandler);
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.info("Parent's Fault Stack : " + faultStack
                    + " : Child's Fault Stack :" + newCtx.getFaultStack());
        }
        
        return newCtx;
    }

    /**
     * This method will simulate cloning the message context and creating an exact copy of the
     * passed message. One should use this method with care; that is because, inside the new MC,
     * most of the attributes of the MC like opCtx and so on are still kept as references. Otherwise
     * there will be perf issues. But ..... this may reveal in some conflicts in the cloned message
     * if you try to do advanced mediations with the cloned message, in which case you should
     * mannually get a clone of the changing part of the MC and set that cloned part to your MC.
     * Changing the MC after doing that will solve most of the issues. (Note: U dont have to worrie
     * about the SOAPEnvelope, it is a cloned copy and not a reference from any other MC)
     *
     * @param mc - this will be cloned for getting an exact copy
     * @return cloned MessageContext from the given mc
     * @throws AxisFault if there is a failure in copying the certain attributes of the
     *          provided message context
     */
    public static org.apache.axis2.context.MessageContext cloneAxis2MessageContext(
        org.apache.axis2.context.MessageContext mc) throws AxisFault {

        org.apache.axis2.context.MessageContext newMC = clonePartially(mc);
        newMC.setEnvelope(cloneSOAPEnvelope(mc.getEnvelope()));
        newMC.setOptions(cloneOptions(mc.getOptions()));
        
        newMC.setServiceContext(mc.getServiceContext());
        newMC.setOperationContext(mc.getOperationContext());
        newMC.setAxisMessage(mc.getAxisMessage());
        if (newMC.getAxisMessage() != null) {
            newMC.getAxisMessage().setParent(mc.getAxisOperation());
        }
        newMC.setAxisService(mc.getAxisService());

        // copying transport related parts from the original
        newMC.setTransportIn(mc.getTransportIn());
        newMC.setTransportOut(mc.getTransportOut());
        newMC.setProperty(org.apache.axis2.Constants.OUT_TRANSPORT_INFO,
            mc.getProperty(org.apache.axis2.Constants.OUT_TRANSPORT_INFO));

        newMC.setProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS,
            getClonedTransportHeaders(mc));

        return newMC;
    }

    public static Map getClonedTransportHeaders(org.apache.axis2.context.MessageContext msgCtx) {
        
        Map headers = (Map) msgCtx.
                getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        Map<String, String> clonedHeaders;
        if (headers instanceof TreeMap) {
            clonedHeaders = new TreeMap<String, String>(new Comparator<String>() {
                public int compare(String s1, String s2) {
                    return s1.compareToIgnoreCase(s2);
                }
            });
        } else {
            clonedHeaders = new HashMap<String, String>();
        }

        if (headers != null && !headers.isEmpty()) {
            for (Object o : headers.keySet()) {
                String headerName = (String) o;
                clonedHeaders.put(headerName, (String) headers.get(headerName));
            }
        }

        return clonedHeaders;
    }

    public static org.apache.axis2.context.MessageContext clonePartially(
        org.apache.axis2.context.MessageContext ori) throws AxisFault {

        org.apache.axis2.context.MessageContext newMC
            = new org.apache.axis2.context.MessageContext();
        
        // do not copy options from the original
        newMC.setConfigurationContext(ori.getConfigurationContext());
        newMC.setMessageID(UIDGenerator.generateURNString());
        newMC.setTo(ori.getTo());
        newMC.setSoapAction(ori.getSoapAction());

        newMC.setProperty(org.apache.axis2.Constants.Configuration.CHARACTER_SET_ENCODING,
                ori.getProperty(org.apache.axis2.Constants.Configuration.CHARACTER_SET_ENCODING));
        newMC.setProperty(org.apache.axis2.Constants.Configuration.ENABLE_MTOM,
                ori.getProperty(org.apache.axis2.Constants.Configuration.ENABLE_MTOM));
        newMC.setProperty(org.apache.axis2.Constants.Configuration.ENABLE_SWA,
                ori.getProperty(org.apache.axis2.Constants.Configuration.ENABLE_SWA));
        newMC.setProperty(Constants.Configuration.HTTP_METHOD,
            ori.getProperty(Constants.Configuration.HTTP_METHOD));
        //coping the Message type from req to res to get the message formatters working correctly.
        newMC.setProperty(Constants.Configuration.MESSAGE_TYPE,
                ori.getProperty(Constants.Configuration.MESSAGE_TYPE));

        newMC.setDoingREST(ori.isDoingREST());
        newMC.setDoingMTOM(ori.isDoingMTOM());
        newMC.setDoingSwA(ori.isDoingSwA());

        // if the original request carries any attachments, copy them to the clone
        // as well, except for the soap part if any
        Attachments attachments = ori.getAttachmentMap();
        if (attachments != null && attachments.getAllContentIDs().length > 0) {
            String[] cIDs = attachments.getAllContentIDs();
            String soapPart = attachments.getSOAPPartContentID();
            for (String cID : cIDs) {
                if (!cID.equals(soapPart)) {
                    newMC.addAttachment(cID, attachments.getDataHandler(cID));
                }
            }
        }

        Iterator itr = ori.getPropertyNames();
        while (itr.hasNext()) {
            String key = (String) itr.next();
            if (key != null) {
                // In a clustered environment, all the properties that need to be relpicated,
                // are replicated explicitly  by the corresponding Mediators (Ex: throttle,
                // cache), and therefore we should avoid any implicit replication
                newMC.setNonReplicableProperty(key, ori.getPropertyNonReplicable(key));
            }
        }

        newMC.setServerSide(false);

        return newMC;
    }

    /**
     * This method will clone the provided SOAPEnvelope and returns the cloned envelope
     * as an exact copy of the provided envelope
     *
     * @param envelope - this will be cloned to get the new envelope
     * @return cloned SOAPEnvelope from the provided one
     */
    public static SOAPEnvelope cloneSOAPEnvelope(SOAPEnvelope envelope) {
        SOAPFactory fac;
        if (SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI
                .equals(envelope.getBody().getNamespace().getNamespaceURI())) {
            fac = OMAbstractFactory.getSOAP11Factory();
        } else {
            fac = OMAbstractFactory.getSOAP12Factory();
        }
        SOAPEnvelope newEnvelope = fac.getDefaultEnvelope();

        if (envelope.getHeader() != null) {
            Iterator itr = envelope.getHeader().cloneOMElement().getChildren();
            while (itr.hasNext()) {
                newEnvelope.getHeader().addChild((OMNode) itr.next());
            }
        }

        if (envelope.getBody() != null) {
            // treat the SOAPFault cloning as a special case otherwise a cloning OMElement as the
            // fault would lead to class cast exceptions if accessed through the getFault method
            if (envelope.getBody().hasFault()) {
                SOAPFault fault = envelope.getBody().getFault();
                newEnvelope.getBody().addFault(cloneSOAPFault(fault));
            } else {
                Iterator itr = envelope.getBody().cloneOMElement().getChildren();
                while (itr.hasNext()) {
                    newEnvelope.getBody().addChild((OMNode) itr.next());
                }
            }
        }

        return newEnvelope;
    }

    /**
     * Clones the given {@link org.apache.axis2.client.Options} object. This is not a deep copy
     * because this will be called for each and every message going out from synapse. The parent
     * of the cloning options object is kept as a reference.
     *
     * @param options clonning object
     * @return clonned Options object
     */
    public static Options cloneOptions(Options options) {

        // create new options object and set the parent
        Options clonedOptions = new Options(options.getParent());

        // copy general options
        clonedOptions.setCallTransportCleanup(options.isCallTransportCleanup());
        clonedOptions.setExceptionToBeThrownOnSOAPFault(options.isExceptionToBeThrownOnSOAPFault());
        clonedOptions.setManageSession(options.isManageSession());
        clonedOptions.setSoapVersionURI(options.getSoapVersionURI());
        clonedOptions.setTimeOutInMilliSeconds(options.getTimeOutInMilliSeconds());
        clonedOptions.setUseSeparateListener(options.isUseSeparateListener());

        // copy transport related options
        clonedOptions.setListener(options.getListener());
        clonedOptions.setTransportIn(options.getTransportIn());
        clonedOptions.setTransportInProtocol(options.getTransportInProtocol());
        clonedOptions.setTransportOut(clonedOptions.getTransportOut());

        // copy username and password options
        clonedOptions.setUserName(options.getUserName());
        clonedOptions.setPassword(options.getPassword());

        // cloen the property set of the current options object
        for (Object o : options.getProperties().keySet()) {
            String key = (String) o;
            clonedOptions.setProperty(key, options.getProperty(key));
        }

        return clonedOptions;
    }

    /**
     * Removes Submission and Final WS-Addressing headers and return the SOAPEnvelope from the given
     * message context
     *
     * @param axisMsgCtx the Axis2 Message context
     * @return the resulting SOAPEnvelope
     */
    public static SOAPEnvelope removeAddressingHeaders(
            org.apache.axis2.context.MessageContext axisMsgCtx) {

        SOAPEnvelope env = axisMsgCtx.getEnvelope();
        SOAPHeader soapHeader = env.getHeader();
        ArrayList addressingHeaders;

        if (soapHeader != null) {
            addressingHeaders =
                soapHeader.getHeaderBlocksWithNSURI(AddressingConstants.Submission.WSA_NAMESPACE);

            if (addressingHeaders != null && addressingHeaders.size() != 0) {
                detachAddressingInformation(addressingHeaders);

            } else {
                addressingHeaders =
                    soapHeader.getHeaderBlocksWithNSURI(AddressingConstants.Final.WSA_NAMESPACE);
                if (addressingHeaders != null && addressingHeaders.size() != 0) {
                    detachAddressingInformation(addressingHeaders);
                }
            }
        }
        return env;
    }

    /**
     * Remove WS-A headers
     *
     * @param headerInformation headers to be removed
     */
    private static void detachAddressingInformation(ArrayList headerInformation) {
        for (Object o : headerInformation) {
            if (o instanceof SOAPHeaderBlock) {
                SOAPHeaderBlock headerBlock = (SOAPHeaderBlock) o;
                headerBlock.detach();
            } else if (o instanceof OMElement) {
                // work around for a known addressing bug which sends non SOAPHeaderBlock objects
                OMElement om = (OMElement) o;
                OMNamespace ns = om.getNamespace();
                if (ns != null && (
                    AddressingConstants.Submission.WSA_NAMESPACE.equals(ns.getNamespaceURI()) ||
                        AddressingConstants.Final.WSA_NAMESPACE.equals(ns.getNamespaceURI()))) {
                    om.detach();
                }
            }
        }
    }

    /**
     * Get the Policy object for the given name from the Synapse configuration at runtime
     * 
     * @param synCtx the current synapse configuration to get to the synapse configuration
     * @param propertyKey the name of the property which holds the Policy required
     * @return the Policy object with the given name, from the configuration
     */
    public static Policy getPolicy(org.apache.synapse.MessageContext synCtx, String propertyKey) {
        Object property = synCtx.getEntry(propertyKey);
        if (property != null && property instanceof OMElement) {
            return PolicyEngine.getPolicy((OMElement) property);
        } else {
            handleException("Cannot locate policy from the property : " + propertyKey);
        }
        return null;
    }

    /**
     * Clones the SOAPFault, fault cloning is not the same as cloning the OMElement because if the
     * Fault is accessed through the SOAPEnvelope.getBody().getFault() method it will lead to a
     * class cast because the cloned element is just an OMElement but not a Fault.
     * 
     * @param fault that needs to be cloned
     * @return the cloned fault
     */
    public static SOAPFault cloneSOAPFault(SOAPFault fault) {

        SOAPFactory fac;
        int soapVersion;
        final int SOAP_11 = 1;
        final int SOAP_12 = 2;
        if (SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI
                .equals(fault.getNamespace().getNamespaceURI())) {
            fac = OMAbstractFactory.getSOAP11Factory();
            soapVersion = SOAP_11;
        } else {
            fac = OMAbstractFactory.getSOAP12Factory();
            soapVersion = SOAP_12;
        }
        SOAPFault newFault = fac.createSOAPFault();

        SOAPFaultCode code = fac.createSOAPFaultCode();
        SOAPFaultReason reason = fac.createSOAPFaultReason();

        switch (soapVersion) {
            case SOAP_11:
                code.setText(fault.getCode().getTextAsQName());
                reason.setText(fault.getReason().getText());
                break;
            case SOAP_12:
                SOAPFaultValue value = fac.createSOAPFaultValue(code);
                value.setText(fault.getCode().getTextAsQName());
                for (Object obj : fault.getReason().getAllSoapTexts()) {
                    SOAPFaultText text = fac.createSOAPFaultText();
                    text.setText(((SOAPFaultText) obj).getText());
                    reason.addSOAPText(text);
                }
                break;
        }

        newFault.setCode(code);
        newFault.setReason(reason);

        if (fault.getNode() != null) {
            SOAPFaultNode soapfaultNode = fac.createSOAPFaultNode();
            soapfaultNode.setNodeValue(fault.getNode().getNodeValue());
            newFault.setNode(soapfaultNode);
        }

        if (fault.getRole() != null) {
            SOAPFaultRole soapFaultRole = fac.createSOAPFaultRole();
            soapFaultRole.setRoleValue(fault.getRole().getRoleValue());
            newFault.setRole(soapFaultRole);
        }

        if (fault.getDetail() != null) {
            SOAPFaultDetail soapFaultDetail = fac.createSOAPFaultDetail();
            for (Iterator itr = fault.getDetail().getAllDetailEntries(); itr.hasNext();) {
                soapFaultDetail.addDetailEntry(((OMElement) itr.next()).cloneOMElement());
            }
            newFault.setDetail(soapFaultDetail);
        }

        return newFault;
    }

    /**
     * Remove the headers that are marked as processed.
     * @param axisMsgCtx the Axis2 Message context
     * @param preserveAddressing if true preserve the addressing headers     
     */
    public static void removeProcessedHeaders(org.apache.axis2.context.MessageContext axisMsgCtx,
                                              boolean preserveAddressing) {
        SOAPEnvelope env = axisMsgCtx.getEnvelope();
        SOAPHeader soapHeader = env.getHeader();

        if (soapHeader != null) {
            Iterator it = soapHeader.getChildElements();
            while (it.hasNext()) {
                Object o = it.next();
                if (o instanceof SOAPHeaderBlock) {
                    SOAPHeaderBlock headerBlock = (SOAPHeaderBlock) o;
                    if (!preserveAddressing) {
                        // if we don't need to preserve addressing headers remove without checking
                        if (headerBlock.isProcessed()) {
                            headerBlock.detach();
                        }
                    } else {
                        // else remove only if not an addressing header
                        if (!isAddressingHeader(headerBlock)) {
                            if (headerBlock.isProcessed()) {
                                headerBlock.detach();
                            }
                        }
                    }
                }
            }
        }        
    }

    /**
     * Return true if the SOAP header is an addressing header
     * @param headerBlock SOAP header block to be checked
     * @return true if the SOAP header is an addressing header
     */
    private static boolean isAddressingHeader(SOAPHeaderBlock headerBlock) {
        OMNamespace ns = headerBlock.getNamespace();
        return ns != null && (
                AddressingConstants.Submission.WSA_NAMESPACE.equals(ns.getNamespaceURI()) ||
                        AddressingConstants.Final.WSA_NAMESPACE.equals(ns.getNamespaceURI()));
    }

    private static void handleException(String msg) {
        log.error(msg);
        throw new SynapseException(msg);
    }

}