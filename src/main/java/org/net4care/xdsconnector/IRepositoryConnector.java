package org.net4care.xdsconnector;

import java.util.List;

import org.net4care.xdsconnector.Utilities.CodedValue;
import org.net4care.xdsconnector.service.RegistryResponseType;
import org.net4care.xdsconnector.service.RetrieveDocumentSetResponseType;
import org.w3c.dom.Document;

public interface IRepositoryConnector {

  RetrieveDocumentSetResponseType retrieveDocumentSet(String docId);
  
  RetrieveDocumentSetResponseType retrieveDocumentSet(List<String> docIds);

  RegistryResponseType provideAndRegisterCDADocument(Document cda,
      CodedValue healthcareFacilityType, CodedValue practiceSettingsCode);

  RegistryResponseType provideAndRegisterCDADocument(String cda,
      CodedValue healthcareFacilityType, CodedValue practiceSettingsCode);

  RegistryResponseType provideAndRegisterCDADocuments(List<String> cdas,
      CodedValue healthcareFacilityType, CodedValue practiceSettingsCode);

}