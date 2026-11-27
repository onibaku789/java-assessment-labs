# locker-soap-api responsibility review

[All modules and counting conventions](README.md)

4 Java files. 4 types, 15 methods and 2 explicit constructors. 1 types and 3 members have multiple reasons to change.

## SoapApp

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class SoapApp`](../../locker-soap-api/src/main/java/study/locker/soap/SoapApp.java#L9) | 1 | Spring Boot startup of the SOAP gateway. |
| [`main(String[])`](../../locker-soap-api/src/main/java/study/locker/soap/SoapApp.java#L12) | 1 | Spring Boot startup of the SOAP gateway. |

## LockerSoap

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class LockerSoap`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L11) | 2 | WSDL operation mapping and generated SOAP response types · Retrieving the active caller credential for the gateway. |
| [`LockerSoap(LockerOperations)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L15) | 1 | WSDL operation mapping and generated SOAP response types. |
| [`actor()`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L19) | 1 | Retrieving the active caller credential for the gateway. |
| [`available(SearchInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L23) | 2 | WSDL operation mapping and generated SOAP response types · Retrieving the active caller credential for the gateway. **Suggested change.** Inject the bearer-token supplier into the adapter and keep WSDL conversion at the endpoint. |
| [`get(KeyInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L37) | 2 | WSDL operation mapping and generated SOAP response types · Retrieving the active caller credential for the gateway. **Suggested change.** Inject the bearer-token supplier into the adapter and keep WSDL conversion at the endpoint. |
| [`reserve(CommandInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L41) | 1 | WSDL operation mapping and generated SOAP response types. |
| [`cancel(CommandInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L45) | 1 | WSDL operation mapping and generated SOAP response types. |
| [`deposit(CommandInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L49) | 1 | WSDL operation mapping and generated SOAP response types. |
| [`reissue(CommandInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L53) | 1 | WSDL operation mapping and generated SOAP response types. |
| [`collect(CommandInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L57) | 1 | WSDL operation mapping and generated SOAP response types. |
| [`command(LockerOperations.Action, CommandInput)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L61) | 2 | WSDL operation mapping and generated SOAP response types · Retrieving the active caller credential for the gateway. **Suggested change.** Inject the bearer-token supplier into the adapter and keep WSDL conversion at the endpoint. |
| [`map(LockerOperations.Reservation)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/LockerSoap.java#L65) | 1 | WSDL operation mapping and generated SOAP response types. |

**Refactor to consider.** Pass credential lookup through a dedicated provider so WSDL mapping can change independently.

## SoapFaults

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class SoapFaults`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/SoapFaults.java#L9) | 1 | Translation of gateway failures into the declared SOAP fault. |
| [`SoapFaults()`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/SoapFaults.java#L10) | 0 | Empty private constructor prevents instantiation and owns no independent behavior. |
| [`call(Supplier<T>)`](../../locker-soap-api/src/main/java/study/locker/soap/adapter/soap/SoapFaults.java#L13) | 1 | Translation of gateway failures into the declared SOAP fault. Translates the gateway code and message into a WSDL fault detail. |

## SoapConfiguration

| Declaration | Reasons | Independent reasons to change |
| --- | ---: | --- |
| [`class SoapConfiguration`](../../locker-soap-api/src/main/java/study/locker/soap/bootstrap/SoapConfiguration.java#L14) | 1 | Publishing the locker WSDL endpoint through CXF and the servlet mapping. |
| [`lockerBus()`](../../locker-soap-api/src/main/java/study/locker/soap/bootstrap/SoapConfiguration.java#L16) | 1 | Publishing the locker WSDL endpoint through CXF and the servlet mapping. |
| [`soapServlet(Bus)`](../../locker-soap-api/src/main/java/study/locker/soap/bootstrap/SoapConfiguration.java#L21) | 1 | Publishing the locker WSDL endpoint through CXF and the servlet mapping. |
| [`lockerEndpoint(Bus, LockerOperations)`](../../locker-soap-api/src/main/java/study/locker/soap/bootstrap/SoapConfiguration.java#L27) | 1 | Publishing the locker WSDL endpoint through CXF and the servlet mapping. |
