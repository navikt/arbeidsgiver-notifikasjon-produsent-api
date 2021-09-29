
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
 *         &lt;element name="SendStandaloneNotificationBasicV2Result" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
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
    "sendStandaloneNotificationBasicV2Result"
})
@XmlRootElement(name = "SendStandaloneNotificationBasicV2Response")
public class SendStandaloneNotificationBasicV2Response {

    @XmlElementRef(name = "SendStandaloneNotificationBasicV2Result", namespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> sendStandaloneNotificationBasicV2Result;

    /**
     * Gets the value of the sendStandaloneNotificationBasicV2Result property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getSendStandaloneNotificationBasicV2Result() {
        return sendStandaloneNotificationBasicV2Result;
    }

    /**
     * Sets the value of the sendStandaloneNotificationBasicV2Result property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setSendStandaloneNotificationBasicV2Result(JAXBElement<String> value) {
        this.sendStandaloneNotificationBasicV2Result = value;
    }

}
