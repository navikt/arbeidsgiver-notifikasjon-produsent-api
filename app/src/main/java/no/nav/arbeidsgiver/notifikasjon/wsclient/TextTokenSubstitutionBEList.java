
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a strongly typed list of TextToken elements that can be accessed by index.
 *             </summary>
 * 
 * <p>Java class for TextTokenSubstitutionBEList complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="TextTokenSubstitutionBEList"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="TextToken" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10}TextToken" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TextTokenSubstitutionBEList", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", propOrder = {
    "textToken"
})
public class TextTokenSubstitutionBEList {

    @XmlElement(name = "TextToken", nillable = true)
    protected List<TextToken> textToken;

    /**
     * Gets the value of the textToken property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the Jakarta XML Binding object.
     * This is why there is not a <CODE>set</CODE> method for the textToken property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getTextToken().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link TextToken }
     * 
     * 
     */
    public List<TextToken> getTextToken() {
        if (textToken == null) {
            textToken = new ArrayList<TextToken>();
        }
        return this.textToken;
    }

}
