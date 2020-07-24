package com.mini;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Scanner;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebSocketConnectionHandler implements Runnable {
    private Socket _client;
    private InputStream _in;
    private OutputStream _out;

    public ArrayList<String> messageStack;

    WebSocketConnectionHandler(Socket client) {
        _client = client;
        messageStack = new ArrayList<>();
        try {
            _in = _client.getInputStream();
            _out = _client.getOutputStream();

            Scanner inputScanner = new Scanner(_in, "UTF-8");

            String data = inputScanner.useDelimiter("\\r\\n\\r\\n").next();
            Matcher getMatcher = Pattern.compile("^GET").matcher(data);
            if(getMatcher.find()) {
                System.out.println("GET Message found");
                Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);
                match.find();

                final String webSocketGUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Sec-WebSocket-Accept: "
                        + Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((match.group(1) + webSocketGUID).getBytes("UTF-8")))
                        + "\r\n\r\n").getBytes("UTF-8");
                _out.write(response, 0, response.length);
            }
        } catch (IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
        }
        System.out.println("New connection: " + _client.getRemoteSocketAddress());
    }


    private void close() {
        System.out.println("Connection say bye bye");
        try {
            _client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String formatHex(byte b){
        return "0x"+Integer.toHexString(Byte.toUnsignedInt(b));
    }

    enum Length {
        len7Bit,
        len16Bit,
        len64Bit
    }

    public void write(String textToSend, boolean useMask) {
        int frameLength = 1 + 1; // (Fin|RSV1-3|) + (Mask|payloadLen)
        Length textLenToUse = Length.len7Bit;

        if(textToSend.length() <= 125){
            // 7 bit length

        } else {
            if(textToSend.length() > 0xFF*2) {
                // 8 byte payload length
                frameLength += 8;
                textLenToUse = Length.len64Bit;
            } else {
                // 2 byte payload length
                frameLength += 2;
                textLenToUse = Length.len16Bit;
            }
        }

        if(useMask) {
            // 4 byte mask
            frameLength += 4;
        }

        frameLength += textToSend.length();

        byte[] outputFrame = new byte[frameLength];
        int byteCounter = 0;
        outputFrame[byteCounter] = (byte)0b10000001;
        byteCounter++;

        switch (textLenToUse){
            case len7Bit:
                outputFrame[byteCounter] = (byte) textToSend.length();
                byteCounter++;
                break;
            case len16Bit:
                // 2 byte length
                outputFrame[byteCounter] = (byte) 126;
                outputFrame[byteCounter+1] = (byte)(textToSend.length() >> 8);
                outputFrame[byteCounter+2] = (byte)(textToSend.length() & 0x00FF);
                byteCounter += 3;
                break;
            case len64Bit:
                // 8 byte length
                outputFrame[1] = (byte) 127;
                throw new Error("Strings this long currently unsupported");
        }

        if (useMask) {
            throw new Error("Generating random masks are currently unsupported currently unsupported");
        }
        for (Byte charByte : textToSend.getBytes(StandardCharsets.UTF_8)) {
            outputFrame[byteCounter] = charByte;
            byteCounter++;
        }

        try {
            _out.write(outputFrame);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
                while (!_client.isClosed()) {
                    /*
                          0                   1                   2                   3
                          0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
                         +-+-+-+-+-------+-+-------------+-------------------------------+
                         |F|R|R|R| opcode|M| Payload len |    Extended payload length    |
                         |I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
                         |N|V|V|V|       |S|             |   (if payload len==126/127)   |
                         | |1|2|3|       |K|             |                               |
                         +-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
                         |     Extended payload length continued, if payload len == 127  |
                         + - - - - - - - - - - - - - - - +-------------------------------+
                         |                               |Masking-key, if MASK set to 1  |
                         +-------------------------------+-------------------------------+
                         | Masking-key (continued)       |          Payload Data         |
                         +-------------------------------- - - - - - - - - - - - - - - - +
                         :                     Payload Data continued ...                :
                         + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
                         |                     Payload Data continued ...                |
                         +---------------------------------------------------------------+
                    */
                    // read the first section of the web socket call
                    byte[] dataIn = _in.readNBytes(2);
                    int fin  = (dataIn[0] & 0b10000000) >> 7;
                    int RSV1 = (dataIn[0] & 0b01000000) >> 6;
                    int RSV2 = (dataIn[0] & 0b00100000) >> 5;
                    int RSV3 = (dataIn[0] & 0b00010000) >> 4;
                    byte opcode = (byte) (dataIn[0] & 0b00001111);
                    int mask = (dataIn[1] & 0b10000000) >> 7;
                    int payloadLength = (dataIn[1] & 0b01111111);


                    byte[] payloadLengthExtensionData = new byte[0];

                    switch (payloadLength){
                        case 126:
                            payloadLength = 0;
                            payloadLengthExtensionData = _in.readNBytes(2);
                            break;
                        case 127:
                            payloadLength = 0;
                            payloadLengthExtensionData = _in.readNBytes(8);
                            break;
                    }

                    for (int i = 0; i < payloadLengthExtensionData.length; i++) {
                        int bitOffset = (payloadLengthExtensionData.length*8) - (i*8);
                        payloadLength = (payloadLength << bitOffset) | Byte.toUnsignedInt(payloadLengthExtensionData[i]);
                    }

                    byte[] maskingKey = new byte[0];
                    if (mask == 1) {
                        maskingKey = _in.readNBytes(4);
                    }

                    byte[] payloadData = _in.readNBytes(payloadLength);

                    byte[] unmaskedBytes = new byte[payloadData.length];

                    for (int i = 0; i < payloadData.length; i++) {
                        byte payloadByte = payloadData[i];
                        int maskIndex = i % 4;
                        unmaskedBytes[i] = (byte) (Byte.toUnsignedInt(payloadByte) ^ Byte.toUnsignedInt(maskingKey[maskIndex]));
                    }

                    String messageReceived = new String(unmaskedBytes, StandardCharsets.UTF_8);
                    messageStack.add(messageReceived);
                    this.write(messageReceived, false);
                }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
