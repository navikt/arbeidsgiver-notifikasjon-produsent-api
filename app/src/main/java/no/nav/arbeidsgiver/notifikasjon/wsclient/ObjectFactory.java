
package no.nav.arbeidsgiver.notifikasjon.wsclient;

import java.math.BigDecimal;
import java.math.BigInteger;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.annotation.XmlElementDecl;
import jakarta.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the no.nav.arbeidsgiver.notifikasjon.wsclient package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {

    private final static QName _AltinnFault_QNAME = new QName("http://www.altinn.no/services/common/fault/2009/10", "AltinnFault");
    private final static QName _AnyType_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "anyType");
    private final static QName _AnyURI_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "anyURI");
    private final static QName _Base64Binary_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "base64Binary");
    private final static QName _Boolean_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "boolean");
    private final static QName _Byte_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "byte");
    private final static QName _DateTime_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "dateTime");
    private final static QName _Decimal_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "decimal");
    private final static QName _Double_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "double");
    private final static QName _Float_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "float");
    private final static QName _Int_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "int");
    private final static QName _Long_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "long");
    private final static QName _QName_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "QName");
    private final static QName _Short_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "short");
    private final static QName _String_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "string");
    private final static QName _UnsignedByte_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "unsignedByte");
    private final static QName _UnsignedInt_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "unsignedInt");
    private final static QName _UnsignedLong_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "unsignedLong");
    private final static QName _UnsignedShort_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "unsignedShort");
    private final static QName _Char_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "char");
    private final static QName _Duration_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "duration");
    private final static QName _Guid_QNAME = new QName("http://schemas.microsoft.com/2003/10/Serialization/", "guid");
    private final static QName _StandaloneNotificationBEList_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2009/10", "StandaloneNotificationBEList");
    private final static QName _StandaloneNotification_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "StandaloneNotification");
    private final static QName _ReceiverEndPointBEList_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "ReceiverEndPointBEList");
    private final static QName _ReceiverEndPoint_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "ReceiverEndPoint");
    private final static QName _TextTokenSubstitutionBEList_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "TextTokenSubstitutionBEList");
    private final static QName _TextToken_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "TextToken");
    private final static QName _Roles_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2015/06", "Roles");
    private final static QName _Service_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2015/06", "Service");
    private final static QName _TransportType_QNAME = new QName("http://schemas.altinn.no/serviceengine/formsengine/2009/10", "TransportType");
    private final static QName _SendNotificationResultList_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "SendNotificationResultList");
    private final static QName _NotificationResult_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "NotificationResult");
    private final static QName _EndPointResultList_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "EndPointResultList");
    private final static QName _EndPointResult_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "EndPointResult");
    private final static QName _SendStandaloneNotificationBasicV2ResponseSendStandaloneNotificationBasicV2Result_QNAME = new QName("http://www.altinn.no/services/ServiceEngine/Notification/2010/10", "SendStandaloneNotificationBasicV2Result");
    private final static QName _SendStandaloneNotificationBasicV3ResponseSendStandaloneNotificationBasicV3Result_QNAME = new QName("http://www.altinn.no/services/ServiceEngine/Notification/2010/10", "SendStandaloneNotificationBasicV3Result");
    private final static QName _EndPointResultName_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "Name");
    private final static QName _EndPointResultReceiverAddress_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "ReceiverAddress");
    private final static QName _EndPointResultRetrieveFromProfile_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "RetrieveFromProfile");
    private final static QName _NotificationResultEndPoints_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "EndPoints");
    private final static QName _NotificationResultNotificationType_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "NotificationType");
    private final static QName _NotificationResultReporteeNumber_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", "ReporteeNumber");
    private final static QName _TextTokenTokenValue_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "TokenValue");
    private final static QName _ReceiverEndPointTransportType_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "TransportType");
    private final static QName _ReceiverEndPointReceiverAddress_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "ReceiverAddress");
    private final static QName _StandaloneNotificationFromAddress_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "FromAddress");
    private final static QName _StandaloneNotificationIsReservable_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "IsReservable");
    private final static QName _StandaloneNotificationNotificationType_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "NotificationType");
    private final static QName _StandaloneNotificationReceiverEndPoints_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "ReceiverEndPoints");
    private final static QName _StandaloneNotificationReporteeNumber_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "ReporteeNumber");
    private final static QName _StandaloneNotificationRoles_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "Roles");
    private final static QName _StandaloneNotificationService_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "Service");
    private final static QName _StandaloneNotificationTextTokens_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "TextTokens");
    private final static QName _StandaloneNotificationUseServiceOwnerShortNameAsSenderOfSms_QNAME = new QName("http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", "UseServiceOwnerShortNameAsSenderOfSms");
    private final static QName _AltinnFaultAltinnErrorMessage_QNAME = new QName("http://www.altinn.no/services/common/fault/2009/10", "AltinnErrorMessage");
    private final static QName _AltinnFaultAltinnExtendedErrorMessage_QNAME = new QName("http://www.altinn.no/services/common/fault/2009/10", "AltinnExtendedErrorMessage");
    private final static QName _AltinnFaultAltinnLocalizedErrorMessage_QNAME = new QName("http://www.altinn.no/services/common/fault/2009/10", "AltinnLocalizedErrorMessage");
    private final static QName _AltinnFaultErrorGuid_QNAME = new QName("http://www.altinn.no/services/common/fault/2009/10", "ErrorGuid");
    private final static QName _AltinnFaultUserGuid_QNAME = new QName("http://www.altinn.no/services/common/fault/2009/10", "UserGuid");
    private final static QName _AltinnFaultUserId_QNAME = new QName("http://www.altinn.no/services/common/fault/2009/10", "UserId");

    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: no.nav.arbeidsgiver.notifikasjon.wsclient
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Test }
     * 
     */
    public Test createTest() {
        return new Test();
    }

    /**
     * Create an instance of {@link TestResponse }
     * 
     */
    public TestResponse createTestResponse() {
        return new TestResponse();
    }

    /**
     * Create an instance of {@link AltinnFault }
     * 
     */
    public AltinnFault createAltinnFault() {
        return new AltinnFault();
    }

    /**
     * Create an instance of {@link SendStandaloneNotificationBasic }
     * 
     */
    public SendStandaloneNotificationBasic createSendStandaloneNotificationBasic() {
        return new SendStandaloneNotificationBasic();
    }

    /**
     * Create an instance of {@link StandaloneNotificationBEList }
     * 
     */
    public StandaloneNotificationBEList createStandaloneNotificationBEList() {
        return new StandaloneNotificationBEList();
    }

    /**
     * Create an instance of {@link SendStandaloneNotificationBasicResponse }
     * 
     */
    public SendStandaloneNotificationBasicResponse createSendStandaloneNotificationBasicResponse() {
        return new SendStandaloneNotificationBasicResponse();
    }

    /**
     * Create an instance of {@link SendStandaloneNotificationBasicV2 }
     * 
     */
    public SendStandaloneNotificationBasicV2 createSendStandaloneNotificationBasicV2() {
        return new SendStandaloneNotificationBasicV2();
    }

    /**
     * Create an instance of {@link SendStandaloneNotificationBasicV2Response }
     * 
     */
    public SendStandaloneNotificationBasicV2Response createSendStandaloneNotificationBasicV2Response() {
        return new SendStandaloneNotificationBasicV2Response();
    }

    /**
     * Create an instance of {@link SendStandaloneNotificationBasicV3 }
     * 
     */
    public SendStandaloneNotificationBasicV3 createSendStandaloneNotificationBasicV3() {
        return new SendStandaloneNotificationBasicV3();
    }

    /**
     * Create an instance of {@link SendStandaloneNotificationBasicV3Response }
     * 
     */
    public SendStandaloneNotificationBasicV3Response createSendStandaloneNotificationBasicV3Response() {
        return new SendStandaloneNotificationBasicV3Response();
    }

    /**
     * Create an instance of {@link SendNotificationResultList }
     * 
     */
    public SendNotificationResultList createSendNotificationResultList() {
        return new SendNotificationResultList();
    }

    /**
     * Create an instance of {@link StandaloneNotification }
     * 
     */
    public StandaloneNotification createStandaloneNotification() {
        return new StandaloneNotification();
    }

    /**
     * Create an instance of {@link ReceiverEndPointBEList }
     * 
     */
    public ReceiverEndPointBEList createReceiverEndPointBEList() {
        return new ReceiverEndPointBEList();
    }

    /**
     * Create an instance of {@link ReceiverEndPoint }
     * 
     */
    public ReceiverEndPoint createReceiverEndPoint() {
        return new ReceiverEndPoint();
    }

    /**
     * Create an instance of {@link TextTokenSubstitutionBEList }
     * 
     */
    public TextTokenSubstitutionBEList createTextTokenSubstitutionBEList() {
        return new TextTokenSubstitutionBEList();
    }

    /**
     * Create an instance of {@link TextToken }
     * 
     */
    public TextToken createTextToken() {
        return new TextToken();
    }

    /**
     * Create an instance of {@link Roles }
     * 
     */
    public Roles createRoles() {
        return new Roles();
    }

    /**
     * Create an instance of {@link Service }
     * 
     */
    public Service createService() {
        return new Service();
    }

    /**
     * Create an instance of {@link NotificationResult }
     * 
     */
    public NotificationResult createNotificationResult() {
        return new NotificationResult();
    }

    /**
     * Create an instance of {@link EndPointResultList }
     * 
     */
    public EndPointResultList createEndPointResultList() {
        return new EndPointResultList();
    }

    /**
     * Create an instance of {@link EndPointResult }
     * 
     */
    public EndPointResult createEndPointResult() {
        return new EndPointResult();
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link AltinnFault }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link AltinnFault }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/common/fault/2009/10", name = "AltinnFault")
    public JAXBElement<AltinnFault> createAltinnFault(AltinnFault value) {
        return new JAXBElement<AltinnFault>(_AltinnFault_QNAME, AltinnFault.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Object }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Object }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "anyType")
    public JAXBElement<Object> createAnyType(Object value) {
        return new JAXBElement<Object>(_AnyType_QNAME, Object.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "anyURI")
    public JAXBElement<String> createAnyURI(String value) {
        return new JAXBElement<String>(_AnyURI_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link byte[]}{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link byte[]}{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "base64Binary")
    public JAXBElement<byte[]> createBase64Binary(byte[] value) {
        return new JAXBElement<byte[]>(_Base64Binary_QNAME, byte[].class, null, ((byte[]) value));
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "boolean")
    public JAXBElement<Boolean> createBoolean(Boolean value) {
        return new JAXBElement<Boolean>(_Boolean_QNAME, Boolean.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Byte }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Byte }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "byte")
    public JAXBElement<Byte> createByte(Byte value) {
        return new JAXBElement<Byte>(_Byte_QNAME, Byte.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link XMLGregorianCalendar }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "dateTime")
    public JAXBElement<XMLGregorianCalendar> createDateTime(XMLGregorianCalendar value) {
        return new JAXBElement<XMLGregorianCalendar>(_DateTime_QNAME, XMLGregorianCalendar.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BigDecimal }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link BigDecimal }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "decimal")
    public JAXBElement<BigDecimal> createDecimal(BigDecimal value) {
        return new JAXBElement<BigDecimal>(_Decimal_QNAME, BigDecimal.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Double }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Double }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "double")
    public JAXBElement<Double> createDouble(Double value) {
        return new JAXBElement<Double>(_Double_QNAME, Double.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Float }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Float }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "float")
    public JAXBElement<Float> createFloat(Float value) {
        return new JAXBElement<Float>(_Float_QNAME, Float.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "int")
    public JAXBElement<Integer> createInt(Integer value) {
        return new JAXBElement<Integer>(_Int_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Long }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Long }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "long")
    public JAXBElement<Long> createLong(Long value) {
        return new JAXBElement<Long>(_Long_QNAME, Long.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link QName }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link QName }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "QName")
    public JAXBElement<QName> createQName(QName value) {
        return new JAXBElement<QName>(_QName_QNAME, QName.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Short }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Short }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "short")
    public JAXBElement<Short> createShort(Short value) {
        return new JAXBElement<Short>(_Short_QNAME, Short.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "string")
    public JAXBElement<String> createString(String value) {
        return new JAXBElement<String>(_String_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Short }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Short }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "unsignedByte")
    public JAXBElement<Short> createUnsignedByte(Short value) {
        return new JAXBElement<Short>(_UnsignedByte_QNAME, Short.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Long }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Long }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "unsignedInt")
    public JAXBElement<Long> createUnsignedInt(Long value) {
        return new JAXBElement<Long>(_UnsignedInt_QNAME, Long.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link BigInteger }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link BigInteger }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "unsignedLong")
    public JAXBElement<BigInteger> createUnsignedLong(BigInteger value) {
        return new JAXBElement<BigInteger>(_UnsignedLong_QNAME, BigInteger.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "unsignedShort")
    public JAXBElement<Integer> createUnsignedShort(Integer value) {
        return new JAXBElement<Integer>(_UnsignedShort_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Integer }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "char")
    public JAXBElement<Integer> createChar(Integer value) {
        return new JAXBElement<Integer>(_Char_QNAME, Integer.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Duration }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Duration }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "duration")
    public JAXBElement<Duration> createDuration(Duration value) {
        return new JAXBElement<Duration>(_Duration_QNAME, Duration.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.microsoft.com/2003/10/Serialization/", name = "guid")
    public JAXBElement<String> createGuid(String value) {
        return new JAXBElement<String>(_Guid_QNAME, String.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StandaloneNotificationBEList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link StandaloneNotificationBEList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2009/10", name = "StandaloneNotificationBEList")
    public JAXBElement<StandaloneNotificationBEList> createStandaloneNotificationBEList(StandaloneNotificationBEList value) {
        return new JAXBElement<StandaloneNotificationBEList>(_StandaloneNotificationBEList_QNAME, StandaloneNotificationBEList.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link StandaloneNotification }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link StandaloneNotification }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "StandaloneNotification")
    public JAXBElement<StandaloneNotification> createStandaloneNotification(StandaloneNotification value) {
        return new JAXBElement<StandaloneNotification>(_StandaloneNotification_QNAME, StandaloneNotification.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReceiverEndPointBEList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link ReceiverEndPointBEList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "ReceiverEndPointBEList")
    public JAXBElement<ReceiverEndPointBEList> createReceiverEndPointBEList(ReceiverEndPointBEList value) {
        return new JAXBElement<ReceiverEndPointBEList>(_ReceiverEndPointBEList_QNAME, ReceiverEndPointBEList.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReceiverEndPoint }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link ReceiverEndPoint }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "ReceiverEndPoint")
    public JAXBElement<ReceiverEndPoint> createReceiverEndPoint(ReceiverEndPoint value) {
        return new JAXBElement<ReceiverEndPoint>(_ReceiverEndPoint_QNAME, ReceiverEndPoint.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TextTokenSubstitutionBEList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link TextTokenSubstitutionBEList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "TextTokenSubstitutionBEList")
    public JAXBElement<TextTokenSubstitutionBEList> createTextTokenSubstitutionBEList(TextTokenSubstitutionBEList value) {
        return new JAXBElement<TextTokenSubstitutionBEList>(_TextTokenSubstitutionBEList_QNAME, TextTokenSubstitutionBEList.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TextToken }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link TextToken }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "TextToken")
    public JAXBElement<TextToken> createTextToken(TextToken value) {
        return new JAXBElement<TextToken>(_TextToken_QNAME, TextToken.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Roles }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Roles }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2015/06", name = "Roles")
    public JAXBElement<Roles> createRoles(Roles value) {
        return new JAXBElement<Roles>(_Roles_QNAME, Roles.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Service }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Service }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/StandaloneNotificationBE/2015/06", name = "Service")
    public JAXBElement<Service> createService(Service value) {
        return new JAXBElement<Service>(_Service_QNAME, Service.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TransportType }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link TransportType }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/serviceengine/formsengine/2009/10", name = "TransportType")
    public JAXBElement<TransportType> createTransportType(TransportType value) {
        return new JAXBElement<TransportType>(_TransportType_QNAME, TransportType.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SendNotificationResultList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link SendNotificationResultList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "SendNotificationResultList")
    public JAXBElement<SendNotificationResultList> createSendNotificationResultList(SendNotificationResultList value) {
        return new JAXBElement<SendNotificationResultList>(_SendNotificationResultList_QNAME, SendNotificationResultList.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link NotificationResult }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link NotificationResult }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "NotificationResult")
    public JAXBElement<NotificationResult> createNotificationResult(NotificationResult value) {
        return new JAXBElement<NotificationResult>(_NotificationResult_QNAME, NotificationResult.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EndPointResultList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link EndPointResultList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "EndPointResultList")
    public JAXBElement<EndPointResultList> createEndPointResultList(EndPointResultList value) {
        return new JAXBElement<EndPointResultList>(_EndPointResultList_QNAME, EndPointResultList.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EndPointResult }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link EndPointResult }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "EndPointResult")
    public JAXBElement<EndPointResult> createEndPointResult(EndPointResult value) {
        return new JAXBElement<EndPointResult>(_EndPointResult_QNAME, EndPointResult.class, null, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", name = "SendStandaloneNotificationBasicV2Result", scope = SendStandaloneNotificationBasicV2Response.class)
    public JAXBElement<String> createSendStandaloneNotificationBasicV2ResponseSendStandaloneNotificationBasicV2Result(String value) {
        return new JAXBElement<String>(_SendStandaloneNotificationBasicV2ResponseSendStandaloneNotificationBasicV2Result_QNAME, String.class, SendStandaloneNotificationBasicV2Response.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link SendNotificationResultList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link SendNotificationResultList }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/ServiceEngine/Notification/2010/10", name = "SendStandaloneNotificationBasicV3Result", scope = SendStandaloneNotificationBasicV3Response.class)
    public JAXBElement<SendNotificationResultList> createSendStandaloneNotificationBasicV3ResponseSendStandaloneNotificationBasicV3Result(SendNotificationResultList value) {
        return new JAXBElement<SendNotificationResultList>(_SendStandaloneNotificationBasicV3ResponseSendStandaloneNotificationBasicV3Result_QNAME, SendNotificationResultList.class, SendStandaloneNotificationBasicV3Response.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "Name", scope = EndPointResult.class)
    public JAXBElement<String> createEndPointResultName(String value) {
        return new JAXBElement<String>(_EndPointResultName_QNAME, String.class, EndPointResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "ReceiverAddress", scope = EndPointResult.class)
    public JAXBElement<String> createEndPointResultReceiverAddress(String value) {
        return new JAXBElement<String>(_EndPointResultReceiverAddress_QNAME, String.class, EndPointResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "RetrieveFromProfile", scope = EndPointResult.class)
    public JAXBElement<Boolean> createEndPointResultRetrieveFromProfile(Boolean value) {
        return new JAXBElement<Boolean>(_EndPointResultRetrieveFromProfile_QNAME, Boolean.class, EndPointResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link EndPointResultList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link EndPointResultList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "EndPoints", scope = NotificationResult.class)
    public JAXBElement<EndPointResultList> createNotificationResultEndPoints(EndPointResultList value) {
        return new JAXBElement<EndPointResultList>(_NotificationResultEndPoints_QNAME, EndPointResultList.class, NotificationResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "NotificationType", scope = NotificationResult.class)
    public JAXBElement<String> createNotificationResultNotificationType(String value) {
        return new JAXBElement<String>(_NotificationResultNotificationType_QNAME, String.class, NotificationResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2015/06", name = "ReporteeNumber", scope = NotificationResult.class)
    public JAXBElement<String> createNotificationResultReporteeNumber(String value) {
        return new JAXBElement<String>(_NotificationResultReporteeNumber_QNAME, String.class, NotificationResult.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "TokenValue", scope = TextToken.class)
    public JAXBElement<String> createTextTokenTokenValue(String value) {
        return new JAXBElement<String>(_TextTokenTokenValue_QNAME, String.class, TextToken.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TransportType }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link TransportType }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "TransportType", scope = ReceiverEndPoint.class)
    public JAXBElement<TransportType> createReceiverEndPointTransportType(TransportType value) {
        return new JAXBElement<TransportType>(_ReceiverEndPointTransportType_QNAME, TransportType.class, ReceiverEndPoint.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "ReceiverAddress", scope = ReceiverEndPoint.class)
    public JAXBElement<String> createReceiverEndPointReceiverAddress(String value) {
        return new JAXBElement<String>(_ReceiverEndPointReceiverAddress_QNAME, String.class, ReceiverEndPoint.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "FromAddress", scope = StandaloneNotification.class)
    public JAXBElement<String> createStandaloneNotificationFromAddress(String value) {
        return new JAXBElement<String>(_StandaloneNotificationFromAddress_QNAME, String.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "IsReservable", scope = StandaloneNotification.class)
    public JAXBElement<Boolean> createStandaloneNotificationIsReservable(Boolean value) {
        return new JAXBElement<Boolean>(_StandaloneNotificationIsReservable_QNAME, Boolean.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "NotificationType", scope = StandaloneNotification.class)
    public JAXBElement<String> createStandaloneNotificationNotificationType(String value) {
        return new JAXBElement<String>(_StandaloneNotificationNotificationType_QNAME, String.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link ReceiverEndPointBEList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link ReceiverEndPointBEList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "ReceiverEndPoints", scope = StandaloneNotification.class)
    public JAXBElement<ReceiverEndPointBEList> createStandaloneNotificationReceiverEndPoints(ReceiverEndPointBEList value) {
        return new JAXBElement<ReceiverEndPointBEList>(_StandaloneNotificationReceiverEndPoints_QNAME, ReceiverEndPointBEList.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "ReporteeNumber", scope = StandaloneNotification.class)
    public JAXBElement<String> createStandaloneNotificationReporteeNumber(String value) {
        return new JAXBElement<String>(_StandaloneNotificationReporteeNumber_QNAME, String.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Roles }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Roles }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "Roles", scope = StandaloneNotification.class)
    public JAXBElement<Roles> createStandaloneNotificationRoles(Roles value) {
        return new JAXBElement<Roles>(_StandaloneNotificationRoles_QNAME, Roles.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Service }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Service }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "Service", scope = StandaloneNotification.class)
    public JAXBElement<Service> createStandaloneNotificationService(Service value) {
        return new JAXBElement<Service>(_StandaloneNotificationService_QNAME, Service.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link TextTokenSubstitutionBEList }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link TextTokenSubstitutionBEList }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "TextTokens", scope = StandaloneNotification.class)
    public JAXBElement<TextTokenSubstitutionBEList> createStandaloneNotificationTextTokens(TextTokenSubstitutionBEList value) {
        return new JAXBElement<TextTokenSubstitutionBEList>(_StandaloneNotificationTextTokens_QNAME, TextTokenSubstitutionBEList.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link Boolean }{@code >}
     */
    @XmlElementDecl(namespace = "http://schemas.altinn.no/services/ServiceEngine/Notification/2009/10", name = "UseServiceOwnerShortNameAsSenderOfSms", scope = StandaloneNotification.class)
    public JAXBElement<Boolean> createStandaloneNotificationUseServiceOwnerShortNameAsSenderOfSms(Boolean value) {
        return new JAXBElement<Boolean>(_StandaloneNotificationUseServiceOwnerShortNameAsSenderOfSms_QNAME, Boolean.class, StandaloneNotification.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/common/fault/2009/10", name = "AltinnErrorMessage", scope = AltinnFault.class)
    public JAXBElement<String> createAltinnFaultAltinnErrorMessage(String value) {
        return new JAXBElement<String>(_AltinnFaultAltinnErrorMessage_QNAME, String.class, AltinnFault.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/common/fault/2009/10", name = "AltinnExtendedErrorMessage", scope = AltinnFault.class)
    public JAXBElement<String> createAltinnFaultAltinnExtendedErrorMessage(String value) {
        return new JAXBElement<String>(_AltinnFaultAltinnExtendedErrorMessage_QNAME, String.class, AltinnFault.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/common/fault/2009/10", name = "AltinnLocalizedErrorMessage", scope = AltinnFault.class)
    public JAXBElement<String> createAltinnFaultAltinnLocalizedErrorMessage(String value) {
        return new JAXBElement<String>(_AltinnFaultAltinnLocalizedErrorMessage_QNAME, String.class, AltinnFault.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/common/fault/2009/10", name = "ErrorGuid", scope = AltinnFault.class)
    public JAXBElement<String> createAltinnFaultErrorGuid(String value) {
        return new JAXBElement<String>(_AltinnFaultErrorGuid_QNAME, String.class, AltinnFault.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/common/fault/2009/10", name = "UserGuid", scope = AltinnFault.class)
    public JAXBElement<String> createAltinnFaultUserGuid(String value) {
        return new JAXBElement<String>(_AltinnFaultUserGuid_QNAME, String.class, AltinnFault.class, value);
    }

    /**
     * Create an instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     * 
     * @param value
     *     Java instance representing xml element's value.
     * @return
     *     the new instance of {@link JAXBElement }{@code <}{@link String }{@code >}
     */
    @XmlElementDecl(namespace = "http://www.altinn.no/services/common/fault/2009/10", name = "UserId", scope = AltinnFault.class)
    public JAXBElement<String> createAltinnFaultUserId(String value) {
        return new JAXBElement<String>(_AltinnFaultUserId_QNAME, String.class, AltinnFault.class, value);
    }

}
