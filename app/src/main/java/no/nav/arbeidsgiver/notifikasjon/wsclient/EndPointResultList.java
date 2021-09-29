
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             A collection of ReceiverEndPointResults.
 *             </summary>
 * 
 * <p>Java class for EndPointResultList complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EndPointResultList"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="EndPointResult" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06}EndPointResult" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EndPointResultList", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", propOrder = {
    "endPointResult"
})
public class EndPointResultList {

    @XmlElement(name = "EndPointResult", nillable = true)
    protected List<EndPointResult> endPointResult;

    /**
     * Gets the value of the endPointResult property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the Jakarta XML Binding object.
     * This is why there is not a <CODE>set</CODE> method for the endPointResult property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getEndPointResult().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link EndPointResult }
     * 
     * 
     */
    public List<EndPointResult> getEndPointResult() {
        if (endPointResult == null) {
            endPointResult = new ArrayList<EndPointResult>();
        }
        return this.endPointResult;
    }

}
