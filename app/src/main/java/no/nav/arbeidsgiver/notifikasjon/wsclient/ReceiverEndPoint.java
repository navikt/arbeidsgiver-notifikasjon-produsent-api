
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a receiver of a notification.
 *             </summary>
 * 
 * <p>Java class for ReceiverEndPoint complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ReceiverEndPoint"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="TransportType" type="{http://schemas.altinn.no/serviceengine/formsengine/2009/10}TransportType" minOccurs="0"/&gt;
 *         &lt;element name="ReceiverAddress" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReceiverEndPoint", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", propOrder = {
    "transportType",
    "receiverAddress"
})
public class ReceiverEndPoint {

    @XmlElementRef(name = "TransportType", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<TransportType> transportType;
    @XmlElementRef(name = "ReceiverAddress", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> receiverAddress;

    /**
     * Gets the value of the transportType property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link TransportType }{@code >}
     *     
     */
    public JAXBElement<TransportType> getTransportType() {
        return transportType;
    }

    /**
     * Sets the value of the transportType property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link TransportType }{@code >}
     *     
     */
    public void setTransportType(JAXBElement<TransportType> value) {
        this.transportType = value;
    }

    /**
     * Gets the value of the receiverAddress property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getReceiverAddress() {
        return receiverAddress;
    }

    /**
     * Sets the value of the receiverAddress property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setReceiverAddress(JAXBElement<String> value) {
        this.receiverAddress = value;
    }

}
