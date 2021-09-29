
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a ReceiverEndpoint that received a SendStandaloneNotification.
 *             </summary>
 * 
 * <p>Java class for EndPointResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EndPointResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="Name" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="ReceiverAddress" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="RetrieveFromProfile" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="TransportType" type="{http://schemas.altinn.no/serviceengine/formsengine/2009/10}TransportType" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EndPointResult", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", propOrder = {
    "name",
    "receiverAddress",
    "retrieveFromProfile",
    "transportType"
})
public class EndPointResult {

    @XmlElementRef(name = "Name", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", type = JAXBElement.class, required = false)
    protected JAXBElement<String> name;
    @XmlElementRef(name = "ReceiverAddress", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", type = JAXBElement.class, required = false)
    protected JAXBElement<String> receiverAddress;
    @XmlElementRef(name = "RetrieveFromProfile", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", type = JAXBElement.class, required = false)
    protected JAXBElement<Boolean> retrieveFromProfile;
    @XmlElement(name = "TransportType")
    @XmlSchemaType(name = "string")
    protected TransportType transportType;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setName(JAXBElement<String> value) {
        this.name = value;
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

    /**
     * Gets the value of the retrieveFromProfile property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     *     
     */
    public JAXBElement<Boolean> getRetrieveFromProfile() {
        return retrieveFromProfile;
    }

    /**
     * Sets the value of the retrieveFromProfile property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     *     
     */
    public void setRetrieveFromProfile(JAXBElement<Boolean> value) {
        this.retrieveFromProfile = value;
    }

    /**
     * Gets the value of the transportType property.
     * 
     * @return
     *     possible object is
     *     {@link TransportType }
     *     
     */
    public TransportType getTransportType() {
        return transportType;
    }

    /**
     * Sets the value of the transportType property.
     * 
     * @param value
     *     allowed object is
     *     {@link TransportType }
     *     
     */
    public void setTransportType(TransportType value) {
        this.transportType = value;
    }

}
