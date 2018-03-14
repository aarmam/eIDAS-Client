# Liidese spetsifikatsioon
-----------

## **Otspunktid**

Kõik päringud ja vastused esitatakse UTF-8 kodeeringus.
HTTPS kasutamine on kohustuslik. Soovituslik on kasutada kahesuunalist HTTPS-i.

Meetod | HTTP päring | Kirjeldus
------------- | ------------- | -------------
[**login**](Service-API.md#login) | **GET** /login | Moodustab ülepiirilise isikutuvastusprotsessi algatamise jaoks vajaliku [päringu](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#6-autentimisp%C3%A4ring) koos HTML ümbersuunamisvormiga.
[**returnUrl**](Service-API.md#returnUrl) | **POST** /returnUrl | Ülepiirilise isikutuvastuse tulemuse kontroll. SAML vastuse valideerimine vastavalt [SAML 2 Web SSO profiilile](https://docs.oasis-open.org/security/saml/v2.0/saml-profiles-2.0-os.pdf) ja [konnektorteenuse spetsifikatsioonile](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#7-autentimisvastus). Kontrollide edukal läbimisel isikuandmete tagastamine.
[**metadata**](Service-API.md#metadata) | **GET** /metadata | Publitseerib eIDAS klient teenuse [SAML metaandmed](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#53-teenusepakkuja-metateave).


<a name="login"></a>
## **login**


### Päring

Parameetrid:

| Parameetri nimi        | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **Country** |	Jah | Parameeter määrab ära tuvastatava kodaniku riigi. Väärtus peab vastama [ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) standardis toodule. |
| **LoA** |	Ei | Parameeter, määrab nõutava eIDAS isikutuvastuse taseme. Lubatud väärtused: `low`, `substantial`, `high`. <br>Kui parameeter on määramata, siis vaikimisi loetakse väärtuseks `substantial`. |
| **RelayState** |	Ei | Parameeter, mis saadetakse edasi konnektorteenusele muutmata kujul. Väärtus peab vastama regulaaravaldisele `[a-zA-Z0-9-_]{0,80}`. |

Näide:
```bash
curl 'https://localhost:8889/login?country=CA'
```

```bash
curl 'https://localhost:8889/login?country=CA&LoA=low'
```

```bash
curl 'https://localhost:8889/login?country=CA&LoA=low&RelayState=kse2vna8221lyauej'
```


### Vastus

Eduka vastuse korral tagastatakse HTTP staatuskood 200 koos sihtriiki suunamiseks vajaliku SAML päringu ja HTML ümbersuunamisvormiga. Vea korral moodustatakse vastus vastavalt [**veakäsitlus**](Service-API.md#veakasitlus) peatükis toodule.

| Atribuudi nimi        | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **country** |	Jah | Parameeter määrab ära tuvastatava kodaniku riigi. Väärtus peab vastama [ISO 3166-1 alpha-2](https://en.wikipedia.org/wiki/ISO_3166-1_alpha-2) standardis toodule. |
| **SAMLRequest** |	Ei | [Konnektorteenuse spetsifikatsioonile](https://e-gov.github.io/eIDAS-Connector/Spetsifikatsioon#6-autentimisp%C3%A4ring) vastav SAML `AuthnRequest` päring.  |
| **RelayState** |	Ei | Parameeter, mis saadetakse edasi konnektorteenusele muutmata kujul. Väärtus peab vastama regulaaravaldisele `[a-zA-Z0-9-_]{0,80}`. |

Näide:
```xml
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
    <body onload="document.forms[0].submit()">
        <noscript>
            <p>
                <strong>Note:</strong> Since your browser does not support JavaScript,
                you must press the Continue button once to proceed.
            </p>
        </noscript>
        <form action="https&#x3a;&#x2f;&#x2f;eidastest.eesti.ee/&#x3a;8080&#x2f;EidasNode&#x2f;ServiceProvider" method="post">
            <div>
                <input type="hidden" name="SAMLRequest" value="PD94bWw...........MnA6QXV0aG5SZXF1ZXN0Pg=="/>
                <input type="hidden" name="country" value="CA"/>
            </div>
            <noscript>
                <div>
                    <input type="submit" value="Continue"/>
                </div>
            </noscript>
        </form>
    </body>
</html>

```


------------------------------------------------


<a name="returnUrl"></a>
## **returnUrl**

### Päring

| Päised |
| :-------------------------- |
| `Content-Type: application/x-www-form-urlencoded` |

| Parameeter      | Kohustuslik           | Selgitus  |
| ------------- |:-------------:| :-----|
| **SAMLResponse** | Jah | Ülepiirilisest autentimiskanalist tulev SAML vastus (Base64 kodeeritud). |
| **RelayState** | Ei | Päringuga saadetud `RelayState` parameetri väärtus. Väärtus peab vastama regulaaravaldisele `[a-zA-Z0-9-_]{0,80}`. |

Näide:

```bash
curl -X POST \
  https://localhost:8889/returnUrl \
  -H 'content-type: application/x-www-form-urlencoded' \
  -d 'SAMLResponse=..........................&RelayState=ef27bd52-25e7-11e8-b467-0ed5f89f718b'
```

### Vastus

Edukalt töödeldud vastuse korral tagastatakse HTTP staatuskood 200 ning JSON objekt isikuandmetega. Vea korral moodustatakse vastus vastavalt [**veakäsitlus**](Service-API.md#veakasitlus) peatükis toodud kirjeldusele.

Atribuudi nimi | Kohustuslik | Selgitus | Tüüp
------------ | ------------- | ------------- | -------------
**levelOfAssurance** | Jah  | eIDAS autentimistase. Võimalikud väärtused: `http://eidas.europa.eu/LoA/low`, `http://eidas.europa.eu/LoA/substantial`, `http://eidas.europa.eu/LoA/high` | **String**
**attributes** | Jah | Sisaldab atribuute autenditud isiku andmetega. Atribuudid esitatakse võti-väärtus paaridena, kus võti on `FriendlyName` ja väärtus `AttributeValue` elemendi ladinakeelne sisu vastavalt eIDAS SAML Attribute Profile dokumendile (vt [Viited](https://e-gov.github.io/eIDAS-Connector/Viited)). <br><br>Alati esitatakse minimaalselt neli atribuuti: `FirstName`, `FamilyName`, `PersonIdentifier` ja `DateOfBirth`. | **Objekt**
**attributes.FirstName** | Jah | Isiku eesnimi. | **String**
**attributes.FamilyName** | Jah | Isiku perenimi. | **String**
**attributes.PersonIdentifier** | Jah | Isikut identifitseeriv unikaalne kood. <br><br>Esitatakse formaadis XX+ “/“ + YY + “/“ + ZZZZZZZZZZZ, kus XX on identifitseeritud isiku riigi kood (ISO 3166-1 alpha-2), YY on riigi kood (ISO 3166-1 alpha-2), kus soovitakse isikut autentida ning ZZZZZZZZZZZ isikut identifitseeriv kood. | **String**
**attributes.DateOfBirth** | Jah | Sünniaeg formaadis: YYYY + “-“ + MM + “-“ + DD (kus YYYY on aasta, MM on kuu ning DD päev) | **String**
**attributesNonLatin** | Ei | Sisaldab atribuutide autenditud isiku andmeid mitteladinakeelsel kujul. Atribuudid esitatakse võti-väärtus paaridena, kus võti on `FriendlyName` ja väärtus `AttributeValue` elemendi mitteladinakeelne sisu vastavalt eIDAS SAML Attribute Profile dokumendile (vt [Viited](https://e-gov.github.io/eIDAS-Connector/Viited)). |  **Objekt**


Näide:
```json
{
   "levelOfAssurance":"http://eidas.europa.eu/LoA/substantial",
   "attributes":{
      "DateOfBirth":"1965-01-01",
      "PersonIdentifier":"CA/CA/12345",
      "FamilyName":"Onassis",
      "FirstName":"Alexander"
   },
   "attributesNonLatin":{
      "FamilyName":"Ωνάσης",
      "FirstName":"Αλέξανδρος"
   }
}
```


------------------------------------------------


<a name="metadata"></a>
## **metadata**


### Päring

Parameetrid puuduvad.

Näide:
```bash
curl 'https://localhost:8889/metadata'
```

### Vastus

Eduka vastuse korral tagastatakse HTTP staatuskood 200 ning XML metadata. Vea korral moodustatakse vastus vastavalt [**veakäsitlus**](Service-API.md#veakasitlus) peatükis toodule


Näide:
```xml
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" ID="_dst76fjthbqaxisvsrros6nytpf9m4sz8daw0ch" entityID="https://localhost:8081/metadata" validUntil="2018-03-13T13:40:21.927Z">
	<ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
		<ds:SignedInfo>
			<ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
			<ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"/>
			<ds:Reference URI="#_dst76fjthbqaxisvsrros6nytpf9m4sz8daw0ch">
				<ds:Transforms>
					<ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
					<ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
				</ds:Transforms>
				<ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha512"/>
				<ds:DigestValue>
aX3WTeCMC37Y/qutWVGmwSGFzjjx7+dpoYfvg7RGlkmfGJTSzohUpsZXoHB9W6nKcZoL5MhcscfG Ku4F2ZovIw==
				</ds:DigestValue>
			</ds:Reference>
		</ds:SignedInfo>
		<ds:SignatureValue>
L+5MkF5MyiYZAUl6mCOBdl+d87mLp0m1AaTS/9SLP72K4XZh00iFKh5FMyC+iUiP2nZAgKFWVeNE myR+rl+JejTm3EzdrVbKhRVSEcl+dTpBEZ6APLQZMwe/8KmaRR7L
		</ds:SignatureValue>
		<ds:KeyInfo>
			<ds:X509Data>
				<ds:X509Certificate>
MIIB4jCCAWagAwIBAgIEW1u+vzAMBggqhkjOPQQDAgUAMEcxCzAJBgNVBAYTAkVFMQ0wCwYDVQQK EwR0ZXN0MQ0wCwYDVQQLEwR0ZXN0MRowGAYDVQQDExFTUC1tZXRhZGEtc2lnbmluZzAeFw0xODAz MDkxNjE1NTRaFw0yMDAzMDgxNjE1NTRaMEcxCzAJBgNVBAYTAkVFMQ0wCwYDVQQKEwR0ZXN0MQ0w CwYDVQQLEwR0ZXN0MRowGAYDVQQDExFTUC1tZXRhZGEtc2lnbmluZzB2MBAGByqGSM49AgEGBSuB BAAiA2IABGj1C5gvuR8ZG7Q5b5KSYFV3QzDwo+2aewjBm+SKIotc+5HBUGelflKJn7fKJQfVGwEc I+oVvXcIs0XyV4qQIHT3ylh4SlZg9AUUSZeF2ktLTEHApJ8wHpt89WF+oKqFu6MhMB8wHQYDVR0O BBYEFPd/0ir9wkxXsq1gHdz6CkcSOfQMMAwGCCqGSM49BAMCBQADaAAwZQIxAKab7Kc2NMLyFyMr tGWbHKKq28b5yJoy2//vqjZrVFuRUflYfQnom5Na9za3VYptUQIwPZF083qWwyJNAIK0Qc1c2Lir d0CVMSovoZUCvLmNNWwBUjqTdqIY/3PDO6PRGloT
				</ds:X509Certificate>
			</ds:X509Data>
		</ds:KeyInfo>
	</ds:Signature>
	<md:Extensions xmlns:alg="urn:oasis:names:tc:SAML:metadata:algsupport">
		<eidas:SPType xmlns:eidas="http://eidas.europa.eu/saml-extensions">public</eidas:SPType>
		<alg:SigningMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"/>
	</md:Extensions>
	<md:SPSSODescriptor AuthnRequestsSigned="true" WantAssertionsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
		<md:KeyDescriptor use="signing">
			<ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
				<ds:X509Data>
					<ds:X509Certificate>
MIIB7zCCAXKgAwIBAgIEFWvpjzAMBggqhkjOPQQDAgUAME0xCzAJBgNVBAYTAkVFMQ0wCwYDVQQK EwR0ZXN0MQ0wCwYDVQQLEwR0ZXN0MSAwHgYDVQQDExdTUC1hdXRoLXJlcXVlc3Qtc2lnbmluZzAe Fw0xODAzMDkxNjE1NTVaFw0yMDAzMDgxNjE1NTVaME0xCzAJBgNVBAYTAkVFMQ0wCwYDVQQKEwR0 ZXN0MQ0wCwYDVQQLEwR0ZXN0MSAwHgYDVQQDExdTUC1hdXRoLXJlcXVlc3Qtc2lnbmluZzB2MBAG ByqGSM49AgEGBSuBBAAiA2IABNqM3bEf8xJl3dvpeqM5rF+pJxAw9ao3hFK2D40j8FMmtkTxUt4b f/WQrg0DhW+Qudkdd8nGpzKieF7hIQ1I9WVWW71alaxwcVggR2iD0SpMcnbvjfQ1/zRu16Yw6TjS IaMhMB8wHQYDVR0OBBYEFMeaE0rtTLhOrnBjb/2sDPuuEw+dMAwGCCqGSM49BAMCBQADaQAwZgIx AIW7dSy696VgJkRWYMC3tpqViQGGSXF10qbpXycCSbf5HTvG02OfO/y/lSUduUwsywIxAJEEQZAp JSyRx3O3cmsKqPS/I4lY6pmOfdBCoJK8RRIqHIIIlfvEvoX7koO4wLbgwg==
					</ds:X509Certificate>
				</ds:X509Data>
			</ds:KeyInfo>
		</md:KeyDescriptor>
		<md:KeyDescriptor use="encryption">
			<ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
				<ds:X509Data>
					<ds:X509Certificate>
MIIFNzCCAx+gAwIBAgIEfHFvpTANBgkqhkiG9w0BAQsFADBMMQswCQYDVQQGEwJFRTENMAsGA1UE ChMEdGVzdDENMAsGA1UECxMEdGVzdDEfMB0GA1UEAxMWU1AtcmVzcG9uc2UtZW5jcnlwdGlvbjAe Fw0xODAzMDkxNjE1NTdaFw0yMDAzMDgxNjE1NTdaMEwxCzAJBgNVBAYTAkVFMQ0wCwYDVQQKEwR0 ZXN0MQ0wCwYDVQQLEwR0ZXN0MR8wHQYDVQQDExZTUC1yZXNwb25zZS1lbmNyeXB0aW9uMIICIjAN BgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAiWMUi8QBhP9w5rt32ICTxwDPorbfcqioP4UDmGQf iZjf4+/bzYMO0l6qwJHb1//McQ2KKEgcVGGZgJia9yFjjPSjJlmAKP26aPjTXmmshNGsZG7ErDK4 +Y9B2TXZnDIDbKPLliT4KlCTUbC9YSeWC1/6Z05fn1ggWORBoSmi1vndzfZ7yPHxA0TvvFC6vEGx cnuOh8diF5iYzaWV3MTrxwSFJ2uBKkBOpDStPwZNRS/hEcFPEoRzRU5dPET+YkNkZcQmofzYI9zK t6XDx0dzCWLwBsSNeAwK5Yn84zYNPqFzGE2fCubL7X7eUVaVaXGqU49hJEVKPCNsigQwennuq/GC xt/HtIe9XI4Z+ScbFBvL2CVSUk+562f6jTOBjrJJbrjafWpk51xDFydGWyvYxpKJgmynT0sfyK5r TyK2g1CAkKwLgdxgBi/aoB21DZCdhvmntHjV+DFjaq5TEU9xQCAH2GkUdv8mbzmFUb+vvM7RtUVQ oskMxEM43Y+GoHPgcp2+lDJQ9rTV3INIFwE+XeP3HdnDpKrzeQqmPy1raIUJSpSQ6nG+K6bCbZrL I9wUCVgH6BJ1euD1mOjir4P6yP9+j7j6RCItM9weXPNEeG/ENZFZ9fBKJ+jNdqJW03zuOQWdYPlp YHtOKk46L9JruEF5jMbqXjxfmUuFCSlwPF8CAwEAAaMhMB8wHQYDVR0OBBYEFFJ47K8Dr0b/eIQI HsL6IPs5RJspMA0GCSqGSIb3DQEBCwUAA4ICAQBm1dmD7P3xJ3QBm9evVEAfPpGxp8b+elcceKHP NiWon73SH560cNXq9xgHeF9t4Ta35rptONSg/trxBew5y31MxaE/XRKT7CJcTa/1JKqapCgFS9NA L2O6+uiPJW+9xCEYD0x5xJ1Sq1njwCoGlfyFfh4NABbPmtDHrVHJzjaEHMw5YYHAREYPSLf0GHkS qCZ020qg3QJS0FYk+xOCKM63xDeGFSe+Qeo/bYhowbD65gdXjvNtMumfis7E4375dIUGrpdovm6D IPYb1h/PcoPC3gOaTaC3SnXx/FiSGWgnuRvJfifTCepsdIrojbWUh/2ffTBcTNOlXVC8Azxdud3s 7DaKun6XI3Q6DaQqlc13d4uuqbZG51uCb0GCTt36ATJ3vDs6G0NrKgskRaKmp5CJKAg75jOtq7UT Sg4ItvGvz9V8eMwZBJdqc6KaHcjlq6NCX5NFOHwBKvCsEi6e575w+UsUKliB6FepZ3VdIlC6Iq+X CYs/CwXLb8nZa6k3ZLoW6/K8eukv+5nYGyI3Ubf7Wi2E624hckG2DVBRPXHaWpODgYr5hIQt1FHE wrbTPHQn5yamuAWBhIEMeDgCMlYimW5DpCjm4ncstpTn+u2y6Oy9G6vzIRzI7OsneXEWUYSQAHei pZSiFLgSx7k5bj/6ocA0CxRzhCghhAvAbrqOfQ==
					</ds:X509Certificate>
				</ds:X509Data>
			</ds:KeyInfo>
		</md:KeyDescriptor>
		<md:NameIDFormat>
urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified
		</md:NameIDFormat>
		<md:AssertionConsumerService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://localhost:8081/returnUrl" index="0"/>
	</md:SPSSODescriptor>
</md:EntityDescriptor>
```

--------------------------------------------------

<a name="veakasitlus"></a>
## **Veakäsitlus**

### HTTP staatuskood

HTTP staatuskoode käsitletakse [RFC2616](https://tools.ietf.org/html/rfc2616) standardile vastavalt.


Näiteks tähistavad 400 vahemiku koodid kliendi päringu mittevastavust nõuetele (nagu puuduvad või lubamatu väärtusega parameetrid) ning staatuskoodid alates 500 serveripoolseid probleeme (nagu ülekoormus).

### HTTP vastuse keha

Veakirjeldus tagastatakse JSON objektina.

Atribuudi nimi | Kohustuslik | Selgitus | Tüüp
------------ | ------------- | ------------- | -------------
**error** | Jah  | Vea lühikirjeldus. | **String**
**message** | Jah  | Viga selgitav tekst. | **String**

Näide:
```json
{
   "error" : "Bad Request",
   "message" : "Required String parameter 'country' is not present"
}
```