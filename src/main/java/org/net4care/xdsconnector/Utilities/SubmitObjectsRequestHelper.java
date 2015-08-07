package org.net4care.xdsconnector.Utilities;

import org.net4care.xdsconnector.Constants.COID;
import org.net4care.xdsconnector.Constants.CUUID;
import org.net4care.xdsconnector.service.*;
import org.net4care.xdsconnector.service.ObjectFactory;
import org.springframework.util.StringUtils;
import org.w3c.dom.*;

import javax.xml.xpath.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class SubmitObjectsRequestHelper {
  private static SimpleDateFormat xdsDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
  private static ObjectFactory factory = new ObjectFactory();
  private XPathFactory xpathFactory = XPathFactory.newInstance();
  private XPath xpath = xpathFactory.newXPath();

  private String homeCommunityId;
  private String repositoryId;

  public SubmitObjectsRequestHelper(String repositoryId, String homeCommunityId) {
    this.repositoryId = repositoryId;
    this.homeCommunityId = homeCommunityId;
  }

  //
  // public methods
  //

  public SubmitObjectsRequest buildFromCDA(Document cda) {
    // TODO: should these be parameterized?

    String associatedId = UUID.randomUUID().toString();
    String submissionSetId = UUID.randomUUID().toString();

    SubmitObjectsRequest request = new SubmitObjectsRequest();
    RegistryObjectListType registry = factory.createRegistryObjectListType();
    request.setRegistryObjectList(registry);

    addStableDocumentEntry(registry, cda, associatedId);
    addRegistryPackage(registry, cda, submissionSetId);
    addClassificationNode(registry, submissionSetId);
    addAssociation(registry, submissionSetId, associatedId);

    return request;
  }

  public void addStableDocumentEntry(RegistryObjectListType registry, Document cda, String associatedId) {
    String title = getString(cda, "ClinicalDocument/title");
    ExtrinsicObjectType documentEntry = createStableDocumentEntryObject(associatedId, title);

    addLanguageCode(documentEntry, cda);
    addCreationTime(documentEntry, cda);
    addServiceStartTime(documentEntry, cda);
    addServiceStopTime(documentEntry, cda);
    // TODO: OpenXDS does not allow patientId
    // addPatientId(documentEntry, cda);
    addSourcePatientId(documentEntry, cda);
    addSourcePatientInfo(documentEntry, cda);
    addLegalAuthenticator(documentEntry, cda);

    addFormatCode(documentEntry, cda, associatedId);
    addClassCode(documentEntry, associatedId);
    addTypeCode(documentEntry, cda, associatedId);
    addConfidentialityCode(documentEntry, cda, associatedId);
    addHealthcareFacilityTypeCode(documentEntry, cda, associatedId);
    addPracticeSettingCode(documentEntry, associatedId);
    addEventCodeList(documentEntry, cda, associatedId);

    addDocumentEntryUniqueId(documentEntry, cda, associatedId);
    addDocumentEntryPatientId(documentEntry, cda, associatedId);

    registry.getIdentifiable().add(factory.createExtrinsicObject(documentEntry));
  }

  public void addRegistryPackage(RegistryObjectListType registry, Document cda, String submissionSetId) {
    String title = getString(cda, "ClinicalDocument/title");
    RegistryPackageType registryPackage = factory.createRegistryPackageType();

    registryPackage.setId(submissionSetId);
    registryPackage.setName(createInternationalString(title));

    addSubmissionTime(registryPackage);

    addAuthor(registryPackage, cda, submissionSetId);
    addContentTypeCode(registryPackage, submissionSetId);

    addSubmissionSetUniqueId(registryPackage, cda, submissionSetId);
    addSubmissionSetPatientId(registryPackage, cda, submissionSetId);
    addSourceId(registryPackage, submissionSetId);

    registry.getIdentifiable().add(factory.createRegistryPackage(registryPackage));
  }

  public void addClassificationNode(RegistryObjectListType registry, String submissionSetId) {
    ClassificationType classification = factory.createClassificationType();
    classification.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification");
    classification.setId(java.util.UUID.randomUUID().toString());
    classification.setClassifiedObject(submissionSetId);
    classification.setClassificationNode(CUUID.SubmissionSet.classificationNode);
    registry.getIdentifiable().add(factory.createClassification(classification));
  }

  public void addAssociation(RegistryObjectListType registry, String sourceId, String targetId) {
    AssociationType1 association = factory.createAssociationType1();
    association.setAssociationType("HasMember");
    association.setId(UUID.randomUUID().toString());
    association.setSourceObject(sourceId);
    association.setTargetObject(targetId);
    association.getSlot().add(createSlot("SubmissionSetStatus","Original"));
    registry.getIdentifiable().add(factory.createAssociation(association));
  }

  // 2.2.2, 8, 13, 18, 19, & 31
  public ExtrinsicObjectType createStableDocumentEntryObject(String associatedId, String title) {
    ExtrinsicObjectType extobj = factory.createExtrinsicObjectType();
    extobj.setObjectType(CUUID.DocumentEntry.stableDocument);
    extobj.setMimeType("text/xml");
    extobj.setId(associatedId);
    extobj.setName(createInternationalString(title));
    extobj.setHome(prefixOID(homeCommunityId));
    extobj.setStatus("urn:oasis:names:tc:ebxml-regrep:StatusType:Approved");
    extobj.setDescription(createInternationalString(title));
    return extobj;
  }

  // 2.2.1 author, mandatory
  public void addAuthor(RegistryPackageType registryPackage, Document cda, String submissionSetId) {
    ClassificationType authorClassification = createClassification(CUUID.SubmissionSet.authorId, submissionSetId, "", null);
    addAuthorInstitution(authorClassification, cda);
    addAuthorPerson(authorClassification, cda);
    registryPackage.getClassification().add(authorClassification);
  }

  // 2.2.1.1 authorInstitution, mandatory
  public void addAuthorInstitution(ClassificationType classification, Document cda) {
    String organizationName = getString(cda, "ClinicalDocument/author/assignedAuthor/representedOrganization/name");
    String authorCodeSystem = getString(cda, "ClinicalDocument/author/assignedAuthor/id/@root");
    String authorCode = getString(cda, "ClinicalDocument/author/assignedAuthor/id/@extension");
    classification.getSlot().add(createAuthorInstitution(organizationName, authorCode, authorCodeSystem));
  }

  public SlotType1 createAuthorInstitution(String displayName, String code, String codeSystem) {
    String value = String.format("%s^^^^^&%s&ISO^^^^%s", displayName, codeSystem, code);
    return createSlot("authorInstitution", value);
  }

  // 2.2.1.2 authorPerson, mandatory
  public void addAuthorPerson(ClassificationType classification, Document cda) {
    String authorLastName = getString(cda, "ClinicalDocument/author/assignedAuthor/assignedPerson/name/family");
    List<String> authorGivenNames = getStrings(cda, "ClinicalDocument/author/assignedAuthor/assignedPerson/name/given");
    String authorFirstName = (authorGivenNames.size() > 0) ? authorGivenNames.remove(0) : "";
    classification.getSlot().add(createAuthorPerson(authorLastName, authorFirstName, authorGivenNames.toArray(new String[0])));
  }

  public SlotType1 createAuthorPerson(String lastName, String firstName, String... middleNames) {
    String authorMiddleName = StringUtils.arrayToDelimitedString(middleNames, "&");
    String value = String.format("^%s^%s^%s^^^^^&ISO", lastName, firstName, authorMiddleName);
    return createSlot("authorPerson", value);
  }

  // 2.2.2 availabilityStatus, mandatory
  // part of createStableDocumentEntryObject

  // 2.2.3 classCode, mandatory
  public void addClassCode(ExtrinsicObjectType documentEntry, String associatedId) {
    documentEntry.getClassification().add(createClassCode(associatedId));
  }

  public ClassificationType createClassCode(String associatedId) {
    // Allways clinical report
    return createClassification(CUUID.DocumentEntry.classCode, associatedId, COID.DK.ClassCode_ClinicalReport_Code, COID.DK.ClassCode_ClinicalReport_DisplayName, COID.DK.ClassCode);
  }

  // 2.2.4 comments, not used

  // 2.2.5 confidentialityCode, mandatory
  public void addConfidentialityCode(ExtrinsicObjectType documentEntry, Document cda, String associatedId) {
    String confidentialityCode = getString(cda, "ClinicalDocument/confidentialityCode/@code");
    documentEntry.getClassification().add(createConfidentialityCode(associatedId, confidentialityCode));
  }

  public ClassificationType createConfidentialityCode(String associatedId, String valueId) {
    if (StringUtils.hasLength(valueId)) valueId="N";
    String valueName = confidialityCode2DisplayName(valueId);
    return createClassification(CUUID.DocumentEntry.confidentialityCode, associatedId, valueId, valueName, COID.HL7.Confidentiality);
  }

  // 2.2.6 contentTypeCode, not used
  public void addContentTypeCode(RegistryPackageType registryPackage, String submissionSetId) {
    registryPackage.getClassification().add(createContentTypeCode(submissionSetId));
  }

  public ClassificationType createContentTypeCode(String submissionSetId) {
    // unused, but required, inserting empty values
    return createClassification(CUUID.SubmissionSet.contentTypeCode, submissionSetId, "", "", "");
  }

  // 2.2.7 creationTime, mandatory
  public void addCreationTime(ExtrinsicObjectType documentEntry, Document cda) {
    String creationTime = getString(cda, "ClinicalDocument/effectiveTime/@value");
    documentEntry.getSlot().add(createCreationTime(creationTime));
  }

  public SlotType1 createCreationTime(Date creationTime) {
    return createCreationTime(xdsDateFormat.format(creationTime));
  }

  public SlotType1 createCreationTime(String creationTime) {
    return createSlot("creationTime", creationTime.substring(0,14));
  }

  // 2.2.8 entryUUID, mandatory
  // part of createStableDocumentEntryObject

  // 2.2.9 eventCodeList, required when known
  // TODO: this is as specified in the Danish XDS metadata profile, but it is not correct CDA.
  // The code element is defined to be a CE CWE string with ActCode.
  public void addEventCodeList(ExtrinsicObjectType documentEntry, Document cda, String associatedId) {
    List<String> eventCodes = getStrings(cda, "ClinicalDocument/documentationOf/serviceEvent/code/@code");
    List<String> eventNames = getStrings(cda, "ClinicalDocument/documentationOf/serviceEvent/code/@displayName");
    List<String> eventCodeSystems = getStrings(cda, "ClinicalDocument/documentationOf/serviceEvent/code/@codeSystem");
    int eventSize = Math.min(eventCodes.size(), Math.min(eventNames.size(), eventCodeSystems.size()));
    // log warning at different sizes
    List<ClassificationType> list = new ArrayList<ClassificationType>();
    for (int i=0; i<eventSize; i++) {
      list.add(createEventCodeList(associatedId, eventCodeSystems.get(i), eventCodes.get(i), eventNames.get(i)));
    }
    documentEntry.getClassification().addAll(list);
  }

  public ClassificationType createEventCodeList(String associatedId, String eventCodeSystem, String eventCode, String eventName) {
    return createClassification(CUUID.DocumentEntry.eventCodeList, associatedId, eventCode, eventName, eventCodeSystem);
  }

  // 2.2.10 formatCode, mandatory
  public void addFormatCode(ExtrinsicObjectType documentEntry, Document cda, String associatedId) {
    ClassificationType classification = null;
    String templateId = getString(cda, "ClinicalDocument/templateId/@root");
    switch (templateId) {
      case "1.2.208.184.11.1":
        classification = createFormatCode(associatedId, COID.DK.FormatCode_PHMR_Code, COID.DK.FormatCode_PHMR_DisplayName);
        break;
      case "1.2.208.184.13.1":
        // TODO: get QRD format code, handle multiple template ids
        // classification = createFormatCode(associatedId, COID.DK.FormatCode_QRD_Code, COID.DK.FormatCode_QRD_DisplayName);
        break;
      case "2.16.840.1.113883.10.20.32":
        // TODO: get QFDD format code, handle multiple template ids
        // classification = createFormatCode(associatedId, COID.DK.FormatCode_QFDD_Code, COID.DK.FormatCode_QFDD_DisplayName);
        break;
      default:
        // TODO: get generic CDA format code, handle multiple template ids
        // classification = createFormatCode(associatedId, COID.DK.FormatCode_CDA_Code, COID.DK.FormatCode_CDA_DisplayName);
        break;
    }
    if (classification != null) documentEntry.getClassification().add(classification);
  }

  public ClassificationType createFormatCode(String associatedId, String code, String displayName) {
    return createClassification(CUUID.DocumentEntry.formatCode, associatedId, code, displayName, COID.DK.FormatCode);
  }

  // 2.2.11 hash, mandatory
  // added by repository

  // 2.2.12 healthcareFacilityTypeCode, mandatory
  public void addHealthcareFacilityTypeCode(ExtrinsicObjectType documentEntry, Document cda, String associatedId) {
    // TODO: info not in CDA, locked to hospital for now. Should be configurable or parametrized.
    String facilityCodeSystem = "2.16.840.1.113883.3.4208.100.11";
    String facilityCode = "22232009";
    String facilityName = healthcareFacilityTypeCode2DisplayName(facilityCode);
    documentEntry.getClassification().add(createHealthcareFacilityTypeCode(associatedId, facilityCodeSystem, facilityCode, facilityName));
  }

  public ClassificationType createHealthcareFacilityTypeCode(String associatedId, String facilityCode, String facilityId, String facilityName) {
    return createClassification(CUUID.DocumentEntry.healthcareFacilityTypeCode, associatedId, facilityId, facilityName, facilityCode);
  }

  // 2.2.13 homeCommunityId, mandatory
  // part of createStableDocumentEntryObject

  // 2.2.14 intendedRecepient, not used

  // 2.2.15 launguageCode, mandatory
  public void addLanguageCode(ExtrinsicObjectType documentEntry, Document cda) {
    String languageCode = getString(cda, "ClinicalDocument/languageCode/@code");
    documentEntry.getSlot().add(createLanguageCode(languageCode));
  }

  public SlotType1 createLanguageCode(String languageCode) {
    return createSlot("languageCode", languageCode);
  }

  // 2.2.16 legalAuthenticator, required when known
  public void addLegalAuthenticator(ExtrinsicObjectType documentEntry, Document cda) {
    String legalLastName = getString(cda, "ClinicalDocument/legalAuthenticator/assignedEntity/assignedPerson/name/family");
    List<String> legalGivenNames = getStrings(cda, "ClinicalDocument/legalAuthenticator/assignedEntity/assignedPerson/name/given");
    String legalFirstName = (legalGivenNames.size() > 0) ? legalGivenNames.remove(0) : "";
    if (legalLastName.length() > 0 || legalFirstName.length() > 0 || legalGivenNames.size() > 0) {
      documentEntry.getSlot().add(createLegalAuthenticator(legalLastName, legalFirstName, (String[]) legalGivenNames.toArray(new String[0])));
    }
  }

  public SlotType1 createLegalAuthenticator(String lastName, String firstName, String... middleNames) {
    String value = String.format("^%s^%s^%s^^^^^^^&&ISO", lastName, firstName, StringUtils.arrayToDelimitedString(middleNames, "&"));
    return createSlot("legalAuthenticator", value);
  }

  // 2.2.17 limitedMetadata, not used

  // 2.2.18 mimeType, mandatory
  // part of createStableDocumentEntryObject

  // 2.2.19 objectType, mandatory
  // part of createStableDocumentEntryObject

  // 2.2.20 patientId, mandatory
  public void addPatientId(ExtrinsicObjectType documentEntry, Document cda) {
    String patientId = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@extension");
    String patientCodeSystem = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@root");
    documentEntry.getSlot().add(createPatientId(patientCodeSystem, patientId));
  }

  public SlotType1 createPatientId(String patientCodeSystem, String patientId) {
    String value = formatPatientId(patientCodeSystem, patientId);
    return createSlot("patientId", value);
  }

  public void addDocumentEntryPatientId(ExtrinsicObjectType documentEntry, Document cda, String associatedId) {
    String patientId = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@extension");
    String patientCodeSystem = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@root");
    documentEntry.getExternalIdentifier().add(createDocumentEntryPatientId(associatedId, patientCodeSystem, patientId));
  }

  public ExternalIdentifierType createDocumentEntryPatientId(String associatedId, String patientCodeSystem, String patientId) {
    String value = formatPatientId(patientCodeSystem, patientId);
    return createExternalIdentifier(CUUID.DocumentEntry.patientId, associatedId, "XDSDocumentEntry.patientId", value);
  }

  public void addSubmissionSetPatientId(RegistryPackageType registryPackage, Document cda, String submissionSetId) {
    String patientId = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@extension");
    String patientCodeSystem = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@root");
    registryPackage.getExternalIdentifier().add(createSubmissionSetPatientId(submissionSetId, patientCodeSystem, patientId));
  }

  public ExternalIdentifierType createSubmissionSetPatientId(String submissionSetId, String patientCodeSystem, String patientId) {
    String value = formatPatientId(patientCodeSystem, patientId);
    return createExternalIdentifier(CUUID.SubmissionSet.patientId, submissionSetId, "XDSSubmissionSet.patientId", value);
  }

  public String formatPatientId(String patientCodeSystem, String patientId) {
    return String.format("%s^^^&%s&ISO", patientId, patientCodeSystem);
  }

  public String formatPatientId(Document cda) {
    String patientId = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@extension");
    String patientCodeSystem = getString(cda, "ClinicalDocument/recordTarget/patientRole/id/@root");
    return formatPatientId(patientCodeSystem, patientId);
  }

  // 2.2.21 practiceSettingCode, not used
  public void addPracticeSettingCode(ExtrinsicObjectType documentEntry, String associatedId) {
    // unused, but required, inserting empty values
    documentEntry.getClassification().add(createPracticeSettingCode(associatedId));
  }

  public ClassificationType createPracticeSettingCode(String associatedId) {
    // unused, but required, inserting empty values
    return createClassification(CUUID.DocumentEntry.practiceSettingCode, associatedId, "", "", "");
  }

  // 2.2.22 referenceIdList, optional
  // TODO: ignored for now

  // 2.2.23 repositoryUniqueId, mandatory
  // added by repository

  // 2.2.24 serviceStartTime, required when known
  public void addServiceStartTime(ExtrinsicObjectType documentEntry, Document cda) {
    String serviceStartTime = getString(cda, "ClinicalDocument/documentationOf/serviceEvent/effectiveTime/low/@value");
    if (serviceStartTime.length() > 0) documentEntry.getSlot().add(createServiceStartTime(serviceStartTime));
  }

  public SlotType1 createServiceStartTime(Date serviceStartTime) {
    return createServiceStartTime(xdsDateFormat.format(serviceStartTime));
  }

  public SlotType1 createServiceStartTime(String serviceStartTime) {
    return createSlot("serviceStartTime", serviceStartTime.substring(0,14));
  }

  // 2.2.25 serviceStopTime, required when known
  public void addServiceStopTime(ExtrinsicObjectType documentEntry, Document cda) {
    String serviceStopTime = getString(cda, "ClinicalDocument/documentationOf/serviceEvent/effectiveTime/high/@value");
    if (serviceStopTime.length() > 0) documentEntry.getSlot().add(createServiceStopTime(serviceStopTime));
  }

  public SlotType1 createServiceStopTime(Date serviceStopTime) {
    return createServiceStopTime(xdsDateFormat.format(serviceStopTime));
  }

  public SlotType1 createServiceStopTime(String serviceStopTime) {
    return createSlot("serviceStopTime", serviceStopTime.substring(0,14));
  }

  // 2.2.26 size, mandatory
  // added by repository

  // 2.2.27 sourceId, not used
  public void addSourceId(RegistryPackageType registryPackage, String submissionSetId) {
    registryPackage.getExternalIdentifier().add(createSourceId(submissionSetId));
  }

  // TODO: not used, but required by XDS, using repositoryId
  public ExternalIdentifierType createSourceId(String submissionSetId) {
    return createExternalIdentifier(CUUID.SubmissionSet.sourceId, submissionSetId, "XDSSubmissionSet.sourceId", repositoryId);
  }

  // 2.2.28 sourcePatientId, mandatory
  public void addSourcePatientId(ExtrinsicObjectType documentEntry, Document cda) {
    String value = formatPatientId(cda);
    documentEntry.getSlot().add(createSlot("sourcePatientId", "PID-3|" + value));
  }

  public SlotType1 createSourcePatientId(String patientCodeSystem, String patientId) {
    String value = formatPatientId(patientCodeSystem, patientId);
    return createSlot("sourcePatientId", "PID-3|" + value);
  }

  // 2.2.29 sourcePatientInfo, mandatory
  public void addSourcePatientInfo(ExtrinsicObjectType documentEntry, Document cda) {
    String patientLastName = getString(cda, "ClinicalDocument/recordTarget/patientRole/patient/name/family");
    List<String> patientGivenNames = getStrings(cda, "ClinicalDocument/recordTarget/patientRole/patient/name/given");
    String patientFirstName = (patientGivenNames.size() > 0) ? patientGivenNames.remove(0) : "";
    String patientMiddleName = StringUtils.collectionToDelimitedString(patientGivenNames, "&");
    String patientBirthTime = getString(cda, "ClinicalDocument/recordTarget/patientRole/patient/birthTime/@value");
    String patientGender = getString(cda, "ClinicalDocument/recordTarget/patientRole/patient/administrativeGenderCode/@code");

    List<String> values = new ArrayList<String>();
    values.add(String.format("PID-5|%s^%s^%s^^^", patientLastName, patientFirstName, patientMiddleName));
    values.add(String.format("PID-7|%s", patientBirthTime.substring(0,8)));
    values.add(String.format("PID-8|%s", patientGender));
    documentEntry.getSlot().add(createSlot("sourcePatientInfo", values.toArray(new String[0])));
  }

  // 2.2.30 submissionTime, mandatory
  public void addSubmissionTime(RegistryPackageType registryPackage) {
    String submissionTime = xdsDateFormat.format(new Date());
    registryPackage.getSlot().add(createSubmissionTime(submissionTime));
  }

  public SlotType1 createSubmissionTime(Date submissionTime) {
    return createSubmissionTime(xdsDateFormat.format(submissionTime));
  }

  public SlotType1 createSubmissionTime(String submissionTime) {
    return createSlot("submissionTime", submissionTime.substring(0,14));
  }

  // 2.2.31 title, mandatory
  // part of createStableDocumentEntryObject

  // 2.2.32 typeCode, mandatory
  public void addTypeCode(ExtrinsicObjectType documentEntry, Document cda, String associatedId) {
    String typeCode = getString(cda, "ClinicalDocument/code/@code");
    String typeName = getString(cda, "ClinicalDocument/code/@displayName");
    String typeSystem = getString(cda, "ClinicalDocument/code/@codeSystem");
    documentEntry.getClassification().add(createTypeCode(associatedId, typeSystem, typeCode, typeName));
  }

  public ClassificationType createTypeCode(String associatedId, String typeSystem, String typeCode, String typeName) {
    return createClassification(CUUID.DocumentEntry.typeCode, associatedId, typeCode, typeName, typeSystem);
  }

  // 2.2.33 uniqueId, mandatory
  public void addDocumentEntryUniqueId(ExtrinsicObjectType documentEntry, Document cda, String associatedId) {
    String root = getString(cda, "ClinicalDocument/id/@root");
    String extension = getString(cda, "ClinicalDocument/id/@extension");
    documentEntry.getExternalIdentifier().add(createDocumentEntryUniqueId(associatedId, root, extension));
  }

  public ExternalIdentifierType createDocumentEntryUniqueId(String associatedId, String root, String extension) {
    String value = formatUniqueId(root, extension);
    return createExternalIdentifier(CUUID.DocumentEntry.uniqueId, associatedId, "XDSDocumentEntry.uniqueId", value);
  }

  public void addSubmissionSetUniqueId(RegistryPackageType registryPackage, Document cda, String associatedId) {
    String root = getString(cda, "ClinicalDocument/id/@root");
    String extension = getString(cda, "ClinicalDocument/id/@extension");
    registryPackage.getExternalIdentifier().add(createSubmissionSetUniqueId(associatedId, root, extension));
  }

  public ExternalIdentifierType createSubmissionSetUniqueId(String associatedId, String root, String extension) {
    // String value = formatUniqueId(root, extension);
    // TODO: HACK create unique OID
    String value = String.format("%s.%d", root, Long.parseLong(extension.replace("-", "").substring(0,15), 16));
    return createExternalIdentifier(CUUID.SubmissionSet.uniqueId, associatedId, "XDSSubmissionSet.uniqueId", value);
  }

  public String formatUniqueId(Document cda) {
    String root = getString(cda, "ClinicalDocument/id/@root");
    String extension = getString(cda, "ClinicalDocument/id/@extension");
    return formatUniqueId(root, extension);
  }

  public String formatUniqueId(String root, String extension) {
    // TODO: HACK, extension may only be 16 characters
    return String.format("%s^%s", root, extension.replace("-", "").substring(0,16));
  }

  //
  // private methods
  //
  private String confidialityCode2DisplayName(String code) {
    // see http://hl7.org/fhir/v3/Confidentiality
    switch (code.toUpperCase()) {
      case "U": return "Unrestricted";
      case "L": return "Low";
      case "M": return "Moderate";
      case "N": return "Normal";
      case "R": return "Restricted";
      case "V": return "Very restricted";
      default: return "";
    }
  }

  private String healthcareFacilityTypeCode2DisplayName(String code) {
    switch (code) {
      case "264372000": return "apotek";
      case "20078004": return "behandlingscenter for stofmisbrugere";
      case "554221000005108": return "bosted";
      case "554031000005103": return "diætistklinik";
      case "546821000005103": return "ergoterapiklinik";
      case "547011000005103": return "fysioterapiklinik";
      case "546811000005109": return "genoptræningscenter";
      case "550621000005101": return "hjemmesygepleje";
      case "22232009": return "hospital";
      case "550631000005103": return "jordemoderklinik";
      case "550641000005106": return "kiropraktor klinik";
      case "550651000005108": return "lægelaboratorium";
      case "394761003": return "lægepraksis";
      case "550661000005105": return "lægevagt";
      case "42665001": return "plejehjem";
      case "554211000005102": return "præhospitals enhed";
      case "550711000005101": return "psykologisk rådgivningsklinik";
      case "550671000005100": return "speciallægepraksis";
      case "554061000005105": return "statsautoriseret fodterapeut";
      case "264361005": return "sundhedscenter";
      case "554041000005106": return "sundhedsforvaltning";
      case "554021000005101": return "sundhedspleje";
      case "550681000005102": return "tandlægepraksis";
      case "550691000005104": return "tandpleje klinik";
      case "550701000005104": return "tandteknisk klinik";
      case "554231000005106": return "vaccinationsklinik";
      case "554051000005108": return "zoneterapiklinik";
      default: return "";
    }
  }

  private String prefixOID(String id) {
    if (id == null) id = "";
    return (id.startsWith("urn:oid:")) ? id : "urn:oid:" + id;
  }

  private String prefixUUID(String id) {
    if (id == null) id = "";
    return (id.startsWith("urn:uuid:")) ? id : "urn:uuid:" + id;
  }

  private String getString(Document cda, String exp) {
    try {
      return (String) xpath.evaluate(exp, cda, XPathConstants.STRING);
    }
    catch (XPathExpressionException ex) {
      return "";
    }
  }

  private List<String> getStrings(Document cda, String exp) {
    List<String> list = new ArrayList<>();
    try {
      NodeList nodes = (NodeList) xpath.evaluate(exp, cda, XPathConstants.NODESET);
      for (int i=0; i<nodes.getLength(); i++) {
        String value = nodes.item(i).getTextContent();
        list.add(value != null ? value : "");
      }
    }
    catch (XPathExpressionException ex) {
    }
    return list;
  }

  // Generates:
  // <rim:LocalizedString value="{value}"/>
  private InternationalStringType createInternationalString(String value) {
    InternationalStringType ist = factory.createInternationalStringType();
    LocalizedStringType lst = factory.createLocalizedStringType();
    lst.setValue(value);
    ist.getLocalizedString().add(lst);
    return ist;
  }

  // Generates:
  // <rim:Slot name="{name}">
  //   <rim:ValueList>
  //     <rim:Value>{value}</rim:Value>
  //     ...
  //   </rim:ValueList>
  // </rim:Slot>
  private SlotType1 createSlot(String name, String... values) {
    SlotType1 slotEntry = factory.createSlotType1();
    slotEntry.setName(name);
    slotEntry.setValueList(factory.createValueListType());
    for (String value : values) {
      slotEntry.getValueList().getValue().add(value);
    }
    return slotEntry;
  }

  // <rim:Classification
  //   objectType="urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification"
  //   id="{generated}"
  //   classificationScheme="{scheme}"
  //   classifiedObject="{object}"
  //   nodeRepresentation="{nodeRep}">
  //   <rim:Name>
  //     <rim:LocalizedString value="{name}"/>
  //   </rim:Name>
  //   <rim:Slot name="codingScheme">
  //     <rim:ValueList>
  //       <rim:Value>{value(s)}</rim:Value>
  //     </rim:ValueList>
  //   </rim:Slot>
  // </rim:Classification>
  private ClassificationType createClassification(String scheme, String object, String nodeRep, String name, String... values) {
    ClassificationType classification = factory.createClassificationType();
    classification.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:Classification");
    classification.setId(java.util.UUID.randomUUID().toString());
    classification.setClassificationScheme(prefixUUID(scheme));
    classification.setClassifiedObject(object);
    if (nodeRep != null) classification.setNodeRepresentation(nodeRep);
    if (name != null) classification.setName(createInternationalString(name));
    if (values != null && values.length > 0) {
      SlotType1 slotEntry = createSlot("codingScheme", values);
      classification.getSlot().add(slotEntry);
    }
    return classification;
  }

  // Generates
  // <rim:ExternalIdentifier identificationScheme="{idScheme}" value="{value}"
  //   id="{generated}"
  //   objectType="urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:ExternalIdentifier"
  //   registryObject="{registryObject}">
  //   <rim:Name>
  //     <rim:LocalizedString value="{name}"/>
  //   </rim:Name>
  // </rim:ExternalIdentifier>
  private ExternalIdentifierType createExternalIdentifier(String idScheme, String registryObject, String name, String value) {
    ExternalIdentifierType externalIdentifier = factory.createExternalIdentifierType();
    externalIdentifier.setId(java.util.UUID.randomUUID().toString());
    externalIdentifier.setObjectType("urn:oasis:names:tc:ebxml-regrep:ObjectType:RegistryObject:ExternalIdentifier");
    externalIdentifier.setIdentificationScheme(prefixUUID(idScheme));
    if (registryObject != null) externalIdentifier.setRegistryObject(registryObject);
    externalIdentifier.setName(createInternationalString(name));
    externalIdentifier.setValue(value);
    return externalIdentifier;
  }
}