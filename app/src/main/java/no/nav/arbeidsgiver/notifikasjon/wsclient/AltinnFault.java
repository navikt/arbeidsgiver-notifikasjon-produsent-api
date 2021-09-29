
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a SOAP fault message used by Altinn to convey exception information to the caller.
 *             </summary>
 * 
 * <p>Java class for AltinnFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AltinnFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="AltinnErrorMessage" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="AltinnExtendedErrorMessage" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="AltinnLocalizedErrorMessage" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="ErrorGuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="ErrorID" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="UserGuid" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="UserId" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AltinnFault", namespace = "http://www.altinn.no/services/common/fault/2009/10", propOrder = {
    "altinnErrorMessage",
    "altinnExtendedErrorMessage",
    "altinnLocalizedErrorMessage",
    "errorGuid",
    "errorID",
    "userGuid",
    "userId"
})
public class AltinnFault {

    @XmlElementRef(name = "AltinnErrorMessage", namespace = "http://www.altinn.no/services/common/fault/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> altinnErrorMessage;
    @XmlElementRef(name = "AltinnExtendedErrorMessage", namespace = "http://www.altinn.no/services/common/fault/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> altinnExtendedErrorMessage;
    @XmlElementRef(name = "AltinnLocalizedErrorMessage", namespace = "http://www.altinn.no/services/common/fault/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> altinnLocalizedErrorMessage;
    @XmlElementRef(name = "ErrorGuid", namespace = "http://www.altinn.no/services/common/fault/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> errorGuid;
    @XmlElement(name = "ErrorID")
    protected Integer errorID;
    @XmlElementRef(name = "UserGuid", namespace = "http://www.altinn.no/services/common/fault/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> userGuid;
    @XmlElementRef(name = "UserId", namespace = "http://www.altinn.no/services/common/fault/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> userId;

    /**
     * Gets the value of the altinnErrorMessage property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getAltinnErrorMessage() {
        return altinnErrorMessage;
    }

    /**
     * Sets the value of the altinnErrorMessage property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setAltinnErrorMessage(JAXBElement<String> value) {
        this.altinnErrorMessage = value;
    }

    /**
     * Gets the value of the altinnExtendedErrorMessage property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getAltinnExtendedErrorMessage() {
        return altinnExtendedErrorMessage;
    }

    /**
     * Sets the value of the altinnExtendedErrorMessage property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setAltinnExtendedErrorMessage(JAXBElement<String> value) {
        this.altinnExtendedErrorMessage = value;
    }

    /**
     * Gets the value of the altinnLocalizedErrorMessage property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getAltinnLocalizedErrorMessage() {
        return altinnLocalizedErrorMessage;
    }

    /**
     * Sets the value of the altinnLocalizedErrorMessage property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setAltinnLocalizedErrorMessage(JAXBElement<String> value) {
        this.altinnLocalizedErrorMessage = value;
    }

    /**
     * Gets the value of the errorGuid property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getErrorGuid() {
        return errorGuid;
    }

    /**
     * Sets the value of the errorGuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setErrorGuid(JAXBElement<String> value) {
        this.errorGuid = value;
    }

    /**
     * Gets the value of the errorID property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getErrorID() {
        return errorID;
    }

    /**
     * Sets the value of the errorID property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setErrorID(Integer value) {
        this.errorID = value;
    }

    /**
     * Gets the value of the userGuid property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getUserGuid() {
        return userGuid;
    }

    /**
     * Sets the value of the userGuid property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setUserGuid(JAXBElement<String> value) {
        this.userGuid = value;
    }

    /**
     * Gets the value of the userId property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getUserId() {
        return userId;
    }

    /**
     * Sets the value of the userId property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setUserId(JAXBElement<String> value) {
        this.userId = value;
    }

}
