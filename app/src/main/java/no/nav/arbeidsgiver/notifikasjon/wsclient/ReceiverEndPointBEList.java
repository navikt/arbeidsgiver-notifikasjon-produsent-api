
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import java.util.ArrayList;
import java.util.List;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <summary>
 *             Represents a strongly typed list of ReceiverEndPoint elements that can be accessed by index.
 *             </summary>
 * 
 * <p>Java class for ReceiverEndPointBEList complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ReceiverEndPointBEList"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ReceiverEndPoint" type="{http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10}ReceiverEndPoint" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ReceiverEndPointBEList", namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", propOrder = {
    "receiverEndPoint"
})
public class ReceiverEndPointBEList {

    @XmlElement(name = "ReceiverEndPoint", nillable = true)
    protected List<ReceiverEndPoint> receiverEndPoint;

    /**
     * Gets the value of the receiverEndPoint property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the Jakarta XML Binding object.
     * This is why there is not a <CODE>set</CODE> method for the receiverEndPoint property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getReceiverEndPoint().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ReceiverEndPoint }
     * 
     * 
     */
    public List<ReceiverEndPoint> getReceiverEndPoint() {
        if (receiverEndPoint == null) {
            receiverEndPoint = new ArrayList<ReceiverEndPoint>();
        }
        return this.receiverEndPoint;
    }

}
