
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.ws.WebFault;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 3.0.0
 * Generated source version: 3.0
 * 
 */
@WebFault(name = "AltinnFault", targetNamespace = "http://www.altinn.no/services/common/fault/2009/10")
public class INotificationAgencyExternalBasicTestAltinnFaultFaultFaultMessage
    extends Exception
{

    /**
     * Java type that goes as soapenv:Fault detail element.
     * 
     */
    private AltinnFault faultInfo;

    /**
     * 
     * @param faultInfo
     * @param message
     */
    public INotificationAgencyExternalBasicTestAltinnFaultFaultFaultMessage(String message, AltinnFault faultInfo) {
        super(message);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @param faultInfo
     * @param cause
     * @param message
     */
    public INotificationAgencyExternalBasicTestAltinnFaultFaultFaultMessage(String message, AltinnFault faultInfo, Throwable cause) {
        super(message, cause);
        this.faultInfo = faultInfo;
    }

    /**
     * 
     * @return
     *     returns fault bean: no.nav.arbeidsgiver.notifikasjon.wsclient.AltinnFault
     */
    public AltinnFault getFaultInfo() {
        return faultInfo;
    }

}
