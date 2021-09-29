
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a TextToken. TextTokens is used to create more dynamic notification texts. A Token can trigger text substitution
 *             between a token number and a token value. 
 *             </summary>
 * 
 * <p>Java class for TextToken complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TextToken"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="TokenNum" type="{http://www.w3.org/2001/XMLSchema}int" minOccurs="0"/&gt;
 *         &lt;element name="TokenValue" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TextToken", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", propOrder = {
    "tokenNum",
    "tokenValue"
})
public class TextToken {

    @XmlElement(name = "TokenNum")
    protected Integer tokenNum;
    @XmlElementRef(name = "TokenValue", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", type = JAXBElement.class, required = false)
    protected JAXBElement<String> tokenValue;

    /**
     * Gets the value of the tokenNum property.
     * 
     * @return
     *     possible object is
     *     {@link Integer }
     *     
     */
    public Integer getTokenNum() {
        return tokenNum;
    }

    /**
     * Sets the value of the tokenNum property.
     * 
     * @param value
     *     allowed object is
     *     {@link Integer }
     *     
     */
    public void setTokenNum(Integer value) {
        this.tokenNum = value;
    }

    /**
     * Gets the value of the tokenValue property.
     * 
     * @return
     *     possible object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public JAXBElement<String> getTokenValue() {
        return tokenValue;
    }

    /**
     * Sets the value of the tokenValue property.
     * 
     * @param value
     *     allowed object is
     *     {@link JAXBElement }{@code <}{@link String }{@code >}
     *     
     */
    public void setTokenValue(JAXBElement<String> value) {
        this.tokenValue = value;
    }

}
