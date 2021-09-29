
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents the result of a SendStandaloneNotificationV3 service call.
 *             </summary>
 * 
 * <p>Java class for SendNotificationResultList complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SendNotificationResultList"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="NotificationResult" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}NotificationResult" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SendNotificationResultList", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", propOrder = {
    "notificationResult"
})
public class SendNotificationResultList {

    @XmlElement(name = "NotificationResult", nillable = true)
    protected List<NotificationResult> notificationResult;

    /**
     * Gets the value of the notificationResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the Jakarta XML Binding object.
     * This is why there is not a <CODE>set</CODE> method for the notificationResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getNotificationResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link NotificationResult }
     * 
     * 
     */
    public List<NotificationResult> getNotificationResult() {
        if (notificationResult == null) {
            notificationResult = new ArrayList<NotificationResult>();
        }
        return this.notificationResult;
    }

}
