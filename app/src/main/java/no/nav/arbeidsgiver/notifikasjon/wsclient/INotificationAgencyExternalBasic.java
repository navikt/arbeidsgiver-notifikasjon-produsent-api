
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.ws.RequestWrapper;
import jakarta.xml.ws.ResponseWrapper;


/**
 * <summary>
 *             External interface for exposing service operations used by AgencySystem for sending
 *             standalone notifications to users
 *             </summary>
 * 
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 3.0.0
 * Generated source version: 3.0
 * 
 */
@WebService(name = "INotificationAgencyExternalBasic", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
@XmlSeeAlso({
    ObjectFactory.class
})
public interface INotificationAgencyExternalBasic {


    /**
     * 
     * @throws INotificationAgencyExternalBasicTestAltinnFaultFaultFaultMessage
     */
    @WebMethod(operationName = "Test", action = "http://www.altinn.no/services/2009/10/IAltinnContractBase/Test")
    @RequestWrapper(localName = "Test", targetNamespace = "http://www.altinn.no/services/2009/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.Test")
    @ResponseWrapper(localName = "TestResponse", targetNamespace = "http://www.altinn.no/services/2009/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.TestResponse")
    public void test()
        throws INotificationAgencyExternalBasicTestAltinnFaultFaultFaultMessage
    ;

    /**
     * <summary>
     *             SendStandaloneNotification operation lets the agency send notifications to users based on templates
     *             </summary>
     *             <param name="systemUserName">
     *             System user name is the relevant agency system name that is registered in Altinn - mandatory parameter
     *             </param>
     *             <param name="systemPassword">
     *             System password is the password for the corresponding registered agency system - mandatory parameter
     *             </param>
     *             <param name="standaloneNotifications">
     *             Contains a list of standalone notification BE with details for the notifications that is to be sent:
     *             ReporteeNumber: The SSN of the receiver of the notification (can be used for lookup in user profile if ReceiverEndPoints.ReceiverAddress is not set -
     *             mandatory parameter,
     *             ReceiverEndPoints: List of mandatory TransportType(SMS, Email or Both) and ReceiverAddress (optionally-if not set profile information will be used)
     *             for the recipient - mandatory parameter,
     *             LanguageID: Localization language for the notification (English 1033, Bokmål 1044, Nynorsk 2068) - mandatory parameter,
     *             NotificationType: identifies the notification template to be used (template needs to be available in database) - mandatory parameter,
     *             TextTokens: TokenNumber and TokenValue pairs of numbers and the corresponding substitution string - provide if template support this feature,
     *             FromAddress: Optional parameter that can be used to set the from field for e-mail notifications, if not set default Altinn address will be used,
     *             ShipmentDateTime: Optional parameter if the notification is to be sent at a later time
     *             </param>
     * 
     * @param systemPassword
     * @param systemUserName
     * @param standaloneNotifications
     * @throws INotificationAgencyExternalBasicSendStandaloneNotificationBasicAltinnFaultFaultFaultMessage
     */
    @WebMethod(operationName = "SendStandaloneNotificationBasic", action = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10/INotificationAgencyExternalBasic/SendStandaloneNotificationBasic")
    @RequestWrapper(localName = "SendStandaloneNotificationBasic", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.SendStandaloneNotificationBasic")
    @ResponseWrapper(localName = "SendStandaloneNotificationBasicResponse", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.SendStandaloneNotificationBasicResponse")
    public void sendStandaloneNotificationBasic(
        @WebParam(name = "systemUserName", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        String systemUserName,
        @WebParam(name = "systemPassword", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        String systemPassword,
        @WebParam(name = "standaloneNotifications", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        StandaloneNotificationBEList standaloneNotifications)
        throws INotificationAgencyExternalBasicSendStandaloneNotificationBasicAltinnFaultFaultFaultMessage
    ;

    /**
     * <summary>
     *             SendStandaloneNotification operation lets the agency send notifications to users based on templates
     *             </summary>
     *             <param name="systemUserName">
     *             System user name is the relevant agency system name that is registered in Altinn - mandatory parameter
     *             </param>
     *             <param name="systemPassword">
     *             System password is the password for the corresponding registered agency system - mandatory parameter
     *             </param>
     *             <param name="standaloneNotifications">
     *             Contains a list of standalone notification BE with details for the notifications that is to be sent:
     *             ReporteeNumber: The SSN of the receiver of the notification (can be used for lookup in user profile if ReceiverEndPoints.ReceiverAddress is not set -
     *             mandatory parameter,
     *             ReceiverEndPoints: List of mandatory TransportType(SMS, Email or Both) and ReceiverAddress (optionally-if not set profile information will be used)
     *             for the recipient - mandatory parameter,
     *             LanguageID: Localization language for the notification (English 1033, Bokmål 1044, Nynorsk 2068) - mandatory parameter,
     *             NotificationType: identifies the notification template to be used (template needs to be available in database) - mandatory parameter,
     *             TextTokens: TokenNumber and TokenValue pairs of numbers and the corresponding substitution string - provide if template support this feature,
     *             FromAddress: Optional parameter that can be used to set the from field for e-mail notifications, if not set default Altinn address will be used,
     *             ShipmentDateTime: Optional parameter if the notification is to be sent at a later time
     *             </param>
     *             <returns>
     *             Returns a success or failure message
     *             </returns>
     * 
     * @param systemPassword
     * @param systemUserName
     * @param standaloneNotifications
     * @return
     *     returns java.lang.String
     * @throws INotificationAgencyExternalBasicSendStandaloneNotificationBasicV2AltinnFaultFaultFaultMessage
     */
    @WebMethod(operationName = "SendStandaloneNotificationBasicV2", action = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10/INotificationAgencyExternalBasic/SendStandaloneNotificationBasicV2")
    @WebResult(name = "SendStandaloneNotificationBasicV2Result", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
    @RequestWrapper(localName = "SendStandaloneNotificationBasicV2", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.SendStandaloneNotificationBasicV2")
    @ResponseWrapper(localName = "SendStandaloneNotificationBasicV2Response", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.SendStandaloneNotificationBasicV2Response")
    public String sendStandaloneNotificationBasicV2(
        @WebParam(name = "systemUserName", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        String systemUserName,
        @WebParam(name = "systemPassword", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        String systemPassword,
        @WebParam(name = "standaloneNotifications", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        StandaloneNotificationBEList standaloneNotifications)
        throws INotificationAgencyExternalBasicSendStandaloneNotificationBasicV2AltinnFaultFaultFaultMessage
    ;

    /**
     * <summary>
     *             SendStandaloneNotification operation lets the agency send notifications to users based on templates.
     *             When Reportee is an Organization and ReceiverEndpoint has no ReceiverAddress, receiverAddresses will be 
     *             generated from the Organization Profile, and additional ReceiverEndPoints will be generated based on the 
     *             UnitReportee profile and supplied ServiceCode.
     *             </summary>
     *             <param name="systemUserName">
     *             System user name is the relevant agency system name that is registered in Altinn - mandatory parameter
     *             </param>
     *             <param name="systemPassword">
     *             System password is the password for the corresponding registered agency system - mandatory parameter
     *             </param>
     *             <param name="standaloneNotifications">
     *             Contains a list of standalone notification BE with details for the notifications that is to be sent:
     *             ReporteeNumber: The SSN of the receiver of the notification (can be used for lookup in user profile if ReceiverEndPoints.ReceiverAddress is not set -
     *             mandatory parameter,
     *             ReceiverEndPoints: List of mandatory TransportType(SMS, Email or Both) and ReceiverAddress (optionally-if not set profile information will be used)
     *             for the recipient - mandatory parameter,
     *             LanguageID: Localization language for the notification (English 1033, Bokmål 1044, Nynorsk 2068) - mandatory parameter,
     *             NotificationType: identifies the notification template to be used (template needs to be available in database) - mandatory parameter,
     *             TextTokens: TokenNumber and TokenValue pairs of numbers and the corresponding substitution string - provide if template support this feature,
     *             FromAddress: Optional parameter that can be used to set the from field for e-mail notifications, if not set default Altinn address will be used,
     *             ShipmentDateTime: Optional parameter if the notification is to be sent at a later time
     *             </param>
     *             <returns>
     *             Returns a success or failure message
     *             </returns>
     * 
     * @param systemPassword
     * @param systemUserName
     * @param standaloneNotifications
     * @return
     *     returns no.nav.arbeidsgiver.notifikasjon.wsclient.SendNotificationResultList
     * @throws INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
     */
    @WebMethod(operationName = "SendStandaloneNotificationBasicV3", action = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10/INotificationAgencyExternalBasic/SendStandaloneNotificationBasicV3")
    @WebResult(name = "SendStandaloneNotificationBasicV3Result", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
    @RequestWrapper(localName = "SendStandaloneNotificationBasicV3", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.SendStandaloneNotificationBasicV3")
    @ResponseWrapper(localName = "SendStandaloneNotificationBasicV3Response", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", className = "no.nav.arbeidsgiver.notifikasjon.wsclient.SendStandaloneNotificationBasicV3Response")
    public SendNotificationResultList sendStandaloneNotificationBasicV3(
        @WebParam(name = "systemUserName", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        String systemUserName,
        @WebParam(name = "systemPassword", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        String systemPassword,
        @WebParam(name = "standaloneNotifications", targetNamespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10")
        StandaloneNotificationBEList standaloneNotifications)
        throws INotificationAgencyExternalBasicSendStandaloneNotificationBasicV3AltinnFaultFaultFaultMessage
    ;

}
