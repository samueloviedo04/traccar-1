/*
 * Copyright 2020 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.protocol;

import io.netty.channel.Channel;
import org.traccar.BaseProtocolDecoder;
import org.traccar.DeviceSession;
import org.traccar.NetworkMessage;
import org.traccar.Protocol;
import org.traccar.helper.BitUtil;
import org.traccar.helper.Parser;
import org.traccar.helper.PatternBuilder;
import org.traccar.helper.UnitsConverter;
import org.traccar.model.Position;

import java.net.SocketAddress;
import java.util.regex.Pattern;

public class MobilogixProtocolDecoder extends BaseProtocolDecoder {

    public MobilogixProtocolDecoder(Protocol protocol) {
        super(protocol);
    }

    private static final Pattern PATTERN = new PatternBuilder()
            .text("[")
            .number("(dddd)-(dd)-(dd) ")         // date (yyyymmdd)
            .number("(dd):(dd):(dd),")           // time (hhmmss)
            .number("Td+,")                      // type
            .number("(d),")                      // archive
            .expression("[^,]+,")                // protocol version
            .expression("([^,]+),")              // serial number
            .number("(xx),")                     // status
            .number("(d+.d+)")                   // battery
            .groupBegin()
            .text(",")
            .number("(d)")                       // valid
            .number("(d)")                       // rssi
            .number("(d),")                      // satellites
            .number("(-?d+.d+),")                // latitude
            .number("(-?d+.d+),")                // longitude
            .number("(d+.?d*),")                 // speed
            .number("(d+.?d*)")                  // course
            .groupEnd("?")
            .any()
            .compile();

    private String decodeAlarm(String type) {
        switch (type) {
            case "T8":
                return Position.ALARM_LOW_BATTERY;
            case "T9":
                return Position.ALARM_VIBRATION;
            case "T10":
                return Position.ALARM_POWER_CUT;
            case "T11":
                return Position.ALARM_LOW_POWER;
            case "T12":
                return Position.ALARM_GEOFENCE_EXIT;
            case "T13":
                return Position.ALARM_OVERSPEED;
            case "T15":
                return Position.ALARM_TOW;
            default:
                return null;
        }
    }

    @Override
    protected Object decode(
            Channel channel, SocketAddress remoteAddress, Object msg) throws Exception {

        String sentence = (String) msg;
        String type = sentence.substring(21, sentence.indexOf(',', 21));

        if (channel != null) {
            String time = sentence.substring(1, 20);
            String response;
            if (type.equals("T1")) {
                response = String.format("[%s,S1,1]", time);
            } else {
                response = String.format("[%s,S%s]", time, type.substring(1));
            }
            channel.writeAndFlush(new NetworkMessage(response, remoteAddress));
        }

        Parser parser = new Parser(PATTERN, sentence);
        if (!parser.matches()) {
            return null;
        }

        Position position = new Position(getProtocolName());

        position.setDeviceTime(parser.nextDateTime());
        if (parser.nextInt() == 0) {
            position.set(Position.KEY_ARCHIVE, true);
        }

        DeviceSession deviceSession = getDeviceSession(channel, remoteAddress, parser.next());
        if (deviceSession == null) {
            return null;
        }
        position.setDeviceId(deviceSession.getDeviceId());

        position.set(Position.KEY_TYPE, type);
        position.set(Position.KEY_ALARM, decodeAlarm(type));

        int status = parser.nextHexInt();
        position.set(Position.KEY_IGNITION, BitUtil.check(status, 2));
        position.set(Position.KEY_MOTION, BitUtil.check(status, 3));
        position.set(Position.KEY_STATUS, status);

        position.set(Position.KEY_BATTERY, parser.nextDouble());

        if (parser.hasNext(7)) {

            position.setValid(parser.nextInt() > 0);
            position.setFixTime(position.getDeviceTime());

            position.set(Position.KEY_RSSI, parser.nextInt());
            position.set(Position.KEY_SATELLITES, parser.nextInt());

            position.setLatitude(parser.nextDouble());
            position.setLongitude(parser.nextDouble());
            position.setSpeed(UnitsConverter.knotsFromKph(parser.nextDouble()));
            position.setCourse(parser.nextDouble());

        } else {

            getLastLocation(position, position.getDeviceTime());

        }

        return position;
    }

}
