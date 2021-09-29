
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
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
 *         &lt;element name="systemUserName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="systemPassword" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="standaloneNotifications" type="{http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2009/10}StandaloneNotificationBEList"/&gt;
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
    "systemUserName",
    "systemPassword",
    "standaloneNotifications"
})
@XmlRootElement(name = "SendStandaloneNotificationBasicV2")
public class SendStandaloneNotificationBasicV2 {

    @XmlElement(required = true)
    protected String systemUserName;
    @XmlElement(required = true)
    protected String systemPassword;
    @XmlElement(required = true)
    protected StandaloneNotificationBEList standaloneNotifications;

    /**
     * Gets the value of the systemUserName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSystemUserName() {
        return systemUserName;
    }

    /**
     * Sets the value of the systemUserName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSystemUserName(String value) {
        this.systemUserName = value;
    }

    /**
     * Gets the value of the systemPassword property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSystemPassword() {
        return systemPassword;
    }

    /**
     * Sets the value of the systemPassword property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSystemPassword(String value) {
        this.systemPassword = value;
    }

    /**
     * Gets the value of the standaloneNotifications property.
     * 
     * @return
     *     possible object is
     *     {@link StandaloneNotificationBEList }
     *     
     */
    public StandaloneNotificationBEList getStandaloneNotifications() {
        return standaloneNotifications;
    }

    /**
     * Sets the value of the standaloneNotifications property.
     * 
     * @param value
     *     allowed object is
     *     {@link StandaloneNotificationBEList }
     *     
     */
    public void setStandaloneNotifications(StandaloneNotificationBEList value) {
        this.standaloneNotifications = value;
    }

}
