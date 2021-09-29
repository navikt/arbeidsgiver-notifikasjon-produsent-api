
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             List of Roles in unit that should receive Notifications.
 *             </summary>
 * 
 * <p>Java class for Service complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Service"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ServiceCode" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ServiceEdition" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Service", namespace = "http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2015/06", propOrder = {
    "serviceCode",
    "serviceEdition"
})
public class Service {

    @XmlElement(name = "ServiceCode", required = true, nillable = true)
    protected String serviceCode;
    @XmlElement(name = "ServiceEdition")
    protected int serviceEdition;

    /**
     * Gets the value of the serviceCode property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getServiceCode() {
        return serviceCode;
    }

    /**
     * Sets the value of the serviceCode property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setServiceCode(String value) {
        this.serviceCode = value;
    }

    /**
     * Gets the value of the serviceEdition property.
     * 
     */
    public int getServiceEdition() {
        return serviceEdition;
    }

    /**
     * Sets the value of the serviceEdition property.
     * 
     */
    public void setServiceEdition(int value) {
        this.serviceEdition = value;
    }

}
