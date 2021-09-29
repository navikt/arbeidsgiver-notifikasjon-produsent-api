
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a strongly typed collection of stand alone notifications.
 *             </summary>
 * 
 * <p>Java class for StandaloneNotificationBEList complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="StandaloneNotificationBEList"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="StandaloneNotification" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10}StandaloneNotification" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "StandaloneNotificationBEList", namespace = "http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2009/10", propOrder = {
    "standaloneNotification"
})
public class StandaloneNotificationBEList {

    @XmlElement(name = "StandaloneNotification", nillable = true)
    protected List<StandaloneNotification> standaloneNotification;

    /**
     * Gets the value of the standaloneNotification property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the Jakarta XML Binding object.
     * This is why there is not a <CODE>set</CODE> method for the standaloneNotification property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getStandaloneNotification().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link StandaloneNotification }
     * 
     * 
     */
    public List<StandaloneNotification> getStandaloneNotification() {
        if (standaloneNotification == null) {
            standaloneNotification = new ArrayList<StandaloneNotification>();
        }
        return this.standaloneNotification;
    }

}
