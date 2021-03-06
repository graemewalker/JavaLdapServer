/*
 * Copyright 2009-2014 UnboundID Corp.
 * All Rights Reserved.
 */
/*
 * Copyright (C) 2009-2014 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */
package com.unboundid.ldap.protocol;



import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.unboundid.asn1.ASN1Buffer;
import com.unboundid.asn1.ASN1BufferSequence;
import com.unboundid.asn1.ASN1Element;
import com.unboundid.asn1.ASN1Enumerated;
import com.unboundid.asn1.ASN1OctetString;
import com.unboundid.asn1.ASN1Sequence;
import com.unboundid.asn1.ASN1StreamReader;
import com.unboundid.asn1.ASN1StreamReaderSequence;
import com.unboundid.ldap.sdk.BindResult;
import com.unboundid.ldap.sdk.Control;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.util.NotMutable;
import com.unboundid.util.InternalUseOnly;
import com.unboundid.util.ThreadSafety;
import com.unboundid.util.ThreadSafetyLevel;

import static com.unboundid.ldap.protocol.ProtocolMessages.*;
import static com.unboundid.util.Debug.*;
import static com.unboundid.util.StaticUtils.*;
import static com.unboundid.util.Validator.*;



/**
 * This class provides an implementation of a bind response protocol op.
 */
@InternalUseOnly()
@NotMutable()
@ThreadSafety(level=ThreadSafetyLevel.COMPLETELY_THREADSAFE)
public final class BindResponseProtocolOp
       implements ProtocolOp
{
  /**
   * The BER type for the server SASL credentials element.
   */
  public static final byte TYPE_SERVER_SASL_CREDENTIALS = (byte) 0x87;



  /**
   * The serial version UID for this serializable class.
   */
  private static final long serialVersionUID = -7757619031268544913L;



  // The server SASL credentials for this bind response.
  private final ASN1OctetString serverSASLCredentials;

  // The result code for this bind response.
  private final int resultCode;

  // The referral URLs for this bind response.
  private final List<String> referralURLs;

  // The diagnostic message for this bind response.
  private final String diagnosticMessage;

  // The matched DN for this bind response.
  private final String matchedDN;



  /**
   * Creates a new instance of this bind response protocol op with the provided
   * information.
   *
   * @param  resultCode             The result code for this response.
   * @param  matchedDN              The matched DN for this response, if
   *                                available.
   * @param  diagnosticMessage      The diagnostic message for this response, if
   *                                any.
   * @param  referralURLs           The list of referral URLs for this response,
   *                                if any.
   * @param  serverSASLCredentials  The server SASL credentials for this
   *                                response, if available.
   */
  public BindResponseProtocolOp(final int resultCode, final String matchedDN,
                                final String diagnosticMessage,
                                final List<String> referralURLs,
                                final ASN1OctetString serverSASLCredentials)
  {
    this.resultCode            = resultCode;
    this.matchedDN             = matchedDN;
    this.diagnosticMessage     = diagnosticMessage;

    if (referralURLs == null)
    {
      this.referralURLs = Collections.emptyList();
    }
    else
    {
      this.referralURLs = Collections.unmodifiableList(referralURLs);
    }

    if (serverSASLCredentials == null)
    {
      this.serverSASLCredentials = null;
    }
    else
    {
      this.serverSASLCredentials = new ASN1OctetString(
           TYPE_SERVER_SASL_CREDENTIALS, serverSASLCredentials.getValue());
    }
  }



  /**
   * Creates a new bind response protocol op from the provided bind result
   * object.
   *
   * @param  result  The LDAP result object to use to create this protocol op.
   */
  public BindResponseProtocolOp(final LDAPResult result)
  {
    resultCode            = result.getResultCode().intValue();
    matchedDN             = result.getMatchedDN();
    diagnosticMessage     = result.getDiagnosticMessage();
    referralURLs          = toList(result.getReferralURLs());

    if (result instanceof BindResult)
    {
      final BindResult br = (BindResult) result;
      serverSASLCredentials = br.getServerSASLCredentials();
    }
    else
    {
      serverSASLCredentials = null;
    }
  }



  /**
   * Creates a new bind response protocol op read from the provided ASN.1 stream
   * reader.
   *
   * @param  reader  The ASN.1 stream reader from which to read the bind
   *                 response.
   *
   * @throws  LDAPException  If a problem occurs while reading or parsing the
   *                         bind response.
   */
  BindResponseProtocolOp(final ASN1StreamReader reader)
       throws LDAPException
  {
    try
    {
      final ASN1StreamReaderSequence opSequence = reader.beginSequence();
      resultCode = reader.readEnumerated();

      String s = reader.readString();
      ensureNotNull(s);
      if (s.length() == 0)
      {
        matchedDN = null;
      }
      else
      {
        matchedDN = s;
      }

      s = reader.readString();
      ensureNotNull(s);
      if (s.length() == 0)
      {
        diagnosticMessage = null;
      }
      else
      {
        diagnosticMessage = s;
      }

      ASN1OctetString creds = null;
      final ArrayList<String> refs = new ArrayList<String>(1);
      while (opSequence.hasMoreElements())
      {
        final byte type = (byte) reader.peek();
        if (type == GenericResponseProtocolOp.TYPE_REFERRALS)
        {
          final ASN1StreamReaderSequence refSequence = reader.beginSequence();
          while (refSequence.hasMoreElements())
          {
            refs.add(reader.readString());
          }
        }
        else if (type == TYPE_SERVER_SASL_CREDENTIALS)
        {
          creds = new ASN1OctetString(type, reader.readBytes());
        }
        else
        {
          throw new LDAPException(ResultCode.DECODING_ERROR,
               ERR_BIND_RESPONSE_INVALID_ELEMENT.get(toHex(type)));
        }
      }

      referralURLs = Collections.unmodifiableList(refs);
      serverSASLCredentials = creds;
    }
    catch (LDAPException le)
    {
      debugException(le);
      throw le;
    }
    catch (Exception e)
    {
      debugException(e);
      throw new LDAPException(ResultCode.DECODING_ERROR,
           ERR_BIND_RESPONSE_CANNOT_DECODE.get(getExceptionMessage(e)), e);
    }
  }



  /**
   * Retrieves the result code for this bind response.
   *
   * @return  The result code for this bind response.
   */
  public int getResultCode()
  {
    return resultCode;
  }



  /**
   * Retrieves the matched DN for this bind response, if any.
   *
   * @return  The matched DN for this bind response, or {@code null} if there is
   *          no matched DN.
   */
  public String getMatchedDN()
  {
    return matchedDN;
  }



  /**
   * Retrieves the diagnostic message for this bind response, if any.
   *
   * @return  The diagnostic message for this bind response, or {@code null} if
   *          there is no diagnostic message.
   */
  public String getDiagnosticMessage()
  {
    return diagnosticMessage;
  }



  /**
   * Retrieves the list of referral URLs for this bind response.
   *
   * @return  The list of referral URLs for this bind response, or an empty list
   *          if there are no referral URLs.
   */
  public List<String> getReferralURLs()
  {
    return referralURLs;
  }



  /**
   * Retrieves the server SASL credentials for this bind response, if any.
   *
   * @return  The server SASL credentials for this bind response, or
   *          {@code null} if there are no server SASL credentials.
   */
  public ASN1OctetString getServerSASLCredentials()
  {
    return serverSASLCredentials;
  }



  /**
   * {@inheritDoc}
   */
  public byte getProtocolOpType()
  {
    return LDAPMessage.PROTOCOL_OP_TYPE_BIND_RESPONSE;
  }



  /**
   * {@inheritDoc}
   */
  public ASN1Element encodeProtocolOp()
  {
    final ArrayList<ASN1Element> elements = new ArrayList<ASN1Element>(5);
    elements.add(new ASN1Enumerated(getResultCode()));

    final String mDN = getMatchedDN();
    if (mDN == null)
    {
      elements.add(new ASN1OctetString());
    }
    else
    {
      elements.add(new ASN1OctetString(mDN));
    }

    final String dm = getDiagnosticMessage();
    if (dm == null)
    {
      elements.add(new ASN1OctetString());
    }
    else
    {
      elements.add(new ASN1OctetString(dm));
    }

    final List<String> refs = getReferralURLs();
    if (! refs.isEmpty())
    {
      final ArrayList<ASN1Element> refElements =
           new ArrayList<ASN1Element>(refs.size());
      for (final String r : refs)
      {
        refElements.add(new ASN1OctetString(r));
      }
      elements.add(new ASN1Sequence(GenericResponseProtocolOp.TYPE_REFERRALS,
           refElements));
    }

    if (serverSASLCredentials != null)
    {
      elements.add(serverSASLCredentials);
    }

    return new ASN1Sequence(LDAPMessage.PROTOCOL_OP_TYPE_BIND_RESPONSE,
         elements);
  }



  /**
   * Decodes the provided ASN.1 element as a bind response protocol op.
   *
   * @param  element  The ASN.1 element to be decoded.
   *
   * @return  The decoded bind response protocol op.
   *
   * @throws  LDAPException  If the provided ASN.1 element cannot be decoded as
   *                         a bind response protocol op.
   */
  public static BindResponseProtocolOp decodeProtocolOp(
                                            final ASN1Element element)
         throws LDAPException
  {
    try
    {
      final ASN1Element[] elements =
           ASN1Sequence.decodeAsSequence(element).elements();
      final int resultCode =
           ASN1Enumerated.decodeAsEnumerated(elements[0]).intValue();

      final String matchedDN;
      final String md =
           ASN1OctetString.decodeAsOctetString(elements[1]).stringValue();
      if (md.length() > 0)
      {
        matchedDN = md;
      }
      else
      {
        matchedDN = null;
      }

      final String diagnosticMessage;
      final String dm =
           ASN1OctetString.decodeAsOctetString(elements[2]).stringValue();
      if (dm.length() > 0)
      {
        diagnosticMessage = dm;
      }
      else
      {
        diagnosticMessage = null;
      }

      ASN1OctetString serverSASLCredentials = null;
      List<String> referralURLs = null;
      if (elements.length > 3)
      {
        for (int i=3; i < elements.length; i++)
        {
          switch (elements[i].getType())
          {
            case GenericResponseProtocolOp.TYPE_REFERRALS:
              final ASN1Element[] refElements =
                   ASN1Sequence.decodeAsSequence(elements[3]).elements();
              referralURLs = new ArrayList<String>(refElements.length);
              for (final ASN1Element e : refElements)
              {
                referralURLs.add(
                     ASN1OctetString.decodeAsOctetString(e).stringValue());
              }
              break;

            case TYPE_SERVER_SASL_CREDENTIALS:
              serverSASLCredentials =
                   ASN1OctetString.decodeAsOctetString(elements[i]);
              break;

            default:
              throw new LDAPException(ResultCode.DECODING_ERROR,
                   ERR_BIND_RESPONSE_INVALID_ELEMENT.get(
                        toHex(elements[i].getType())));
          }
        }
      }

      return new BindResponseProtocolOp(resultCode, matchedDN,
           diagnosticMessage, referralURLs, serverSASLCredentials);
    }
    catch (final LDAPException le)
    {
      debugException(le);
      throw le;
    }
    catch (final Exception e)
    {
      debugException(e);
      throw new LDAPException(ResultCode.DECODING_ERROR,
           ERR_BIND_RESPONSE_CANNOT_DECODE.get(getExceptionMessage(e)),
           e);
    }
  }



  /**
   * {@inheritDoc}
   */
  public void writeTo(final ASN1Buffer buffer)
  {
    final ASN1BufferSequence opSequence =
         buffer.beginSequence(LDAPMessage.PROTOCOL_OP_TYPE_BIND_RESPONSE);
    buffer.addEnumerated(resultCode);
    buffer.addOctetString(matchedDN);
    buffer.addOctetString(diagnosticMessage);

    if (! referralURLs.isEmpty())
    {
      final ASN1BufferSequence refSequence =
           buffer.beginSequence(GenericResponseProtocolOp.TYPE_REFERRALS);
      for (final String s : referralURLs)
      {
        buffer.addOctetString(s);
      }
      refSequence.end();
    }

    if (serverSASLCredentials != null)
    {
      buffer.addElement(serverSASLCredentials);
    }

    opSequence.end();
  }



  /**
   * Creates a new LDAP result object from this response protocol op.
   *
   * @param  controls  The set of controls to include in the LDAP result.  It
   *                   may be empty or {@code null} if no controls should be
   *                   included.
   *
   * @return  The LDAP result that was created.
   */
  public BindResult toBindResult(final Control... controls)
  {
    final String[] refs;
    if (referralURLs.isEmpty())
    {
      refs = NO_STRINGS;
    }
    else
    {
      refs = new String[referralURLs.size()];
      referralURLs.toArray(refs);
    }

    return new BindResult(-1, ResultCode.valueOf(resultCode), diagnosticMessage,
         matchedDN, refs, controls, serverSASLCredentials);
  }



  /**
   * Retrieves a string representation of this protocol op.
   *
   * @return  A string representation of this protocol op.
   */
  @Override()
  public String toString()
  {
    final StringBuilder buffer = new StringBuilder();
    toString(buffer);
    return buffer.toString();
  }



  /**
   * {@inheritDoc}
   */
  public void toString(final StringBuilder buffer)
  {
    buffer.append("BindResponseProtocolOp(resultCode=");
    buffer.append(resultCode);

    if (matchedDN != null)
    {
      buffer.append(", matchedDN='");
      buffer.append(matchedDN);
      buffer.append('\'');
    }

    if (diagnosticMessage != null)
    {
      buffer.append(", diagnosticMessage='");
      buffer.append(diagnosticMessage);
      buffer.append('\'');
    }

    if (! referralURLs.isEmpty())
    {
      buffer.append(", referralURLs={");

      final Iterator<String> iterator = referralURLs.iterator();
      while (iterator.hasNext())
      {
        buffer.append('\'');
        buffer.append(iterator.next());
        buffer.append('\'');
        if (iterator.hasNext())
        {
          buffer.append(',');
        }
      }

      buffer.append('}');
    }
    buffer.append(')');
  }
}
