
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             A collection of SendStandaloneNotificationReceiverEndpointResults for a Notification.
 *             </summary>
 * 
 * <p>Java class for NotificationResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="NotificationResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="EndPoints" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}EndPointResultList" minOccurs="0"/&gt;
 *         &lt;element name="NotificationType" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="ReporteeNumber" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "NotificationResult", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", propOrder = {
    "endPoints",
    "notificationType",
    "reporteeNumber"
})
public class NotificationResult {

    @XmlElementRef(name = "EndPoints", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", type = JAXBElement.class, required = false)
    protected JAXBElement<EndPointResultList> endPoints;
    @XmlElementRef(name = "NotificationType", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", type = JAXBElement.class, required = false)
    protected JAXBElement<String> notificationType;
    @XmlElementRef(name = "ReporteeNumber", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", type = JAXBElement.class, required = false)
    protected JAXBElement<String> reporteeNumber;

    /**
     * Gets the value of the endPoints property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link EndPointResultList }{@code >}
     *     
     */
    public JAXBElement<EndPointResultList> getEndPoints() {
        return endPoints;
    }

    /**
     * Sets the value of the endPoints property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link EndPointResultList }{@code >}
     *     
     */
    public void setEndPoints(JAXBElement<EndPointResultList> value) {
        this.endPoints = value;
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

}
