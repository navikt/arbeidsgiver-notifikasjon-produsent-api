
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlEnumValue;
import jakarta.xml.bind.annotation.XmlType;


/**
 * <p>Java class for TransportType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <pre>
 * &lt;simpleType name="TransportType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="SMS"/&gt;
 *     &lt;enumeration value="Email"/&gt;
 *     &lt;enumeration value="IM"/&gt;
 *     &lt;enumeration value="Both"/&gt;
 *     &lt;enumeration value="SMSPreferred"/&gt;
 *     &lt;enumeration value="EmailPreferred"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "TransportType", namespace = "http://schemas.altinn.no/serviceengine/formsengine/2009/10")
@XmlEnum
public enum TransportType {


    /**
     * <summary>
     *             Specifies that notifications should be sent via SMS.
     *             </summary>
     * 
     */
    SMS("SMS"),

    /**
     * <summary>
     *             Specifies that notifications should be sent via email.
     *             </summary>
     * 
     */
    @XmlEnumValue("Email")
    EMAIL("Email"),

    /**
     * <summary>
     *             Specifies that notifications should be sent via IM.
     *             </summary>
     * 
     */
    IM("IM"),

    /**
     * <summary>
     *             Specifies that notifications should be sent via both SMS and email.
     *             </summary>
     * 
     */
    @XmlEnumValue("Both")
    BOTH("Both"),

    /**
     * <summary>
     *             Specifies that notifications is preferred to be sent via SMS, but should be sent by email if no phone number exist.
     *             </summary>
     * 
     */
    @XmlEnumValue("SMSPreferred")
    SMS_PREFERRED("SMSPreferred"),

    /**
     * <summary>
     *              Specifies that notifications is preferred to be sent via Email, but should be sent by SMS, of no email address exist.
     *             </summary>
     * 
     */
    @XmlEnumValue("EmailPreferred")
    EMAIL_PREFERRED("EmailPreferred");
    private final String value;

    TransportType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static TransportType fromValue(String v) {
        for (TransportType c: TransportType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
