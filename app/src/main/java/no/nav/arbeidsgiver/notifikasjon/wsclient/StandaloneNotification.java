
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import javax.xml.datatype.XMLGregorianCalendar;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlSchemaType;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a stand alone notification.
 *             </summary>
 * 
 * <p>Java class for StandaloneNotification complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StandaloneNotification"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="FromAddress" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="IsReservable" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *         &lt;element name="LanguageID" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="NotificationType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="ReceiverEndPoints" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10}ReceiverEndPointBEList" minOccurs="0"/&gt;
 *         &lt;element name="ReporteeNumber" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="Roles" type="{http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2015/06}Roles" minOccurs="0"/&gt;
 *         &lt;element name="Service" type="{http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2015/06}Service" minOccurs="0"/&gt;
 *         &lt;element name="ShipmentDateTime" type="{http://www.w3.org/2001/XMLSchema}dateTime" minOccurs="0"/&gt;
 *         &lt;element name="TextTokens" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10}TextTokenSubstitutionBEList" minOccurs="0"/&gt;
 *         &lt;element name="UseServiceOwnerShortNameAsSenderOfSms" type="{http://www.w3.org/2001/XMLSchema}boolean" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StandaloneNotification", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", propOrder = {
    "fromAddress",
    "isReservable",
    "languageID",
    "notificationType",
    "receiverEndPoints",
    "reporteeNumber",
    "roles",
    "service",
    "shipmentDateTime",
    "textTokens",
    "useServiceOwnerShortNameAsSenderOfSms"
})
public class StandaloneNotification {

    @XmlElementRef(name = "FromAddress", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> fromAddress;
    @XmlElementRef(name = "IsReservable", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<Boolean> isReservable;
    @XmlElement(name = "LanguageID")
    protected Integer languageID;
    @XmlElementRef(name = "NotificationType", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> notificationType;
    @XmlElementRef(name = "ReceiverEndPoints", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<ReceiverEndPointBEList> receiverEndPoints;
    @XmlElementRef(name = "ReporteeNumber", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> reporteeNumber;
    @XmlElementRef(name = "Roles", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<Roles> roles;
    @XmlElementRef(name = "Service", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<Service> service;
    @XmlElement(name = "ShipmentDateTime")
    @XmlSchemaType(name = "dateTime")
    protected XMLGregorianCalendar shipmentDateTime;
    @XmlElementRef(name = "TextTokens", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<TextTokenSubstitutionBEList> textTokens;
    @XmlElementRef(name = "UseServiceOwnerShortNameAsSenderOfSms", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<Boolean> useServiceOwnerShortNameAsSenderOfSms;

    /**
     * Gets the value of the fromAddress property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getFromAddress() {
        return fromAddress;
    }

    /**
     * Sets the value of the fromAddress property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setFromAddress(JAXBElement<String> value) {
        this.fromAddress = value;
    }

    /**
     * Gets the value of the isReservable property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     *     
     */
    public JAXBElement<Boolean> getIsReservable() {
        return isReservable;
    }

    /**
     * Sets the value of the isReservable property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     *     
     */
    public void setIsReservable(JAXBElement<Boolean> value) {
        this.isReservable = value;
    }

    /**
     * Gets the value of the languageID property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getLanguageID() {
        return languageID;
    }

    /**
     * Sets the value of the languageID property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setLanguageID(Integer value) {
        this.languageID = value;
    }

    /**
     * Gets the value of the notificationType property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getNotificationType() {
        return notificationType;
    }

    /**
     * Sets the value of the notificationType property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setNotificationType(JAXBElement<String> value) {
        this.notificationType = value;
    }

    /**
     * Gets the value of the receiverEndPoints property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link ReceiverEndPointBEList }{@code >}
     *     
     */
    public JAXBElement<ReceiverEndPointBEList> getReceiverEndPoints() {
        return receiverEndPoints;
    }

    /**
     * Sets the value of the receiverEndPoints property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link ReceiverEndPointBEList }{@code >}
     *     
     */
    public void setReceiverEndPoints(JAXBElement<ReceiverEndPointBEList> value) {
        this.receiverEndPoints = value;
    }

    /**
     * Gets the value of the reporteeNumber property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getReporteeNumber() {
        return reporteeNumber;
    }

    /**
     * Sets the value of the reporteeNumber property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setReporteeNumber(JAXBElement<String> value) {
        this.reporteeNumber = value;
    }

    /**
     * Gets the value of the roles property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Roles }{@code >}
     *     
     */
    public JAXBElement<Roles> getRoles() {
        return roles;
    }

    /**
     * Sets the value of the roles property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Roles }{@code >}
     *     
     */
    public void setRoles(JAXBElement<Roles> value) {
        this.roles = value;
    }

    /**
     * Gets the value of the service property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Service }{@code >}
     *     
     */
    public JAXBElement<Service> getService() {
        return service;
    }

    /**
     * Sets the value of the service property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Service }{@code >}
     *     
     */
    public void setService(JAXBElement<Service> value) {
        this.service = value;
    }

    /**
     * Gets the value of the shipmentDateTime property.
     * 
     * @return
     *     possible object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public XMLGregorianCalendar getShipmentDateTime() {
        return shipmentDateTime;
    }

    /**
     * Sets the value of the shipmentDateTime property.
     * 
     * @param value
     *     allowed object is
     *     {@link XMLGregorianCalendar }
     *     
     */
    public void setShipmentDateTime(XMLGregorianCalendar value) {
        this.shipmentDateTime = value;
    }

    /**
     * Gets the value of the textTokens property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link TextTokenSubstitutionBEList }{@code >}
     *     
     */
    public JAXBElement<TextTokenSubstitutionBEList> getTextTokens() {
        return textTokens;
    }

    /**
     * Sets the value of the textTokens property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link TextTokenSubstitutionBEList }{@code >}
     *     
     */
    public void setTextTokens(JAXBElement<TextTokenSubstitutionBEList> value) {
        this.textTokens = value;
    }

    /**
     * Gets the value of the useServiceOwnerShortNameAsSenderOfSms property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     *     
     */
    public JAXBElement<Boolean> getUseServiceOwnerShortNameAsSenderOfSms() {
        return useServiceOwnerShortNameAsSenderOfSms;
    }

    /**
     * Sets the value of the useServiceOwnerShortNameAsSenderOfSms property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     *     
     */
    public void setUseServiceOwnerShortNameAsSenderOfSms(JAXBElement<Boolean> value) {
        this.useServiceOwnerShortNameAsSenderOfSms = value;
    }

}
