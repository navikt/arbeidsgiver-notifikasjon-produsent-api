
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="SendStandaloneNotificationBasicV3Result" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}SendNotificationResultList" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "sendStandaloneNotificationBasicV3Result"
})
@XmlRootElement(name = "SendStandaloneNotificationBasicV3Response")
public class SendStandaloneNotificationBasicV3Response {

    @XmlElementRef(name = "SendStandaloneNotificationBasicV3Result", namespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", type = JAXBElement.class, required = false)
    protected JAXBElement<SendNotificationResultList> sendStandaloneNotificationBasicV3Result;

    /**
     * Gets the value of the sendStandaloneNotificationBasicV3Result property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link SendNotificationResultList }{@code >}
     *     
     */
    public JAXBElement<SendNotificationResultList> getSendStandaloneNotificationBasicV3Result() {
        return sendStandaloneNotificationBasicV3Result;
    }

    /**
     * Sets the value of the sendStandaloneNotificationBasicV3Result property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link SendNotificationResultList }{@code >}
     *     
     */
    public void setSendStandaloneNotificationBasicV3Result(JAXBElement<SendNotificationResultList> value) {
        this.sendStandaloneNotificationBasicV3Result = value;
    }

}
