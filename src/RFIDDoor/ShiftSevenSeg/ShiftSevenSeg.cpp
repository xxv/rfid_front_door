#include "Arduino.h"
#include "ShiftSevenSeg.h"

#define SEG_DP 36

ShiftSevenSeg::ShiftSevenSeg(uint8_t strobe, uint8_t data, uint8_t clock, uint8_t sa, uint8_t sb, uint8_t sc, uint8_t sd, uint8_t se, uint8_t sf, uint8_t sg, uint8_t sdp){
/*     
 *   __A__
 * F|     |B
 *  |__G__|
 * E|     |C
 *  |__D__| o DP
 *
 */
 

static const uint8_t
  S_A = _BV(sa),
  S_B = _BV(sb),
  S_C = _BV(sc),
  S_D = _BV(sd),
  S_E = _BV(se),
  S_F = _BV(sf),
  S_G = _BV(sg),
  S_DP = _BV(sdp);

  this->segments = {
    S_A | S_B | S_C | S_D | S_E | S_F,   // 0
    S_B | S_C,                           // 1
    S_A | S_B | S_G | S_E | S_D,         // 2
    S_A | S_B | S_C | S_D | S_G,         // 3
    S_B | S_C | S_F | S_G,               // 4
    S_A | S_C | S_D | S_F | S_G,         // 5
    S_A | S_C | S_D | S_E | S_F | S_G,   // 6
    S_A | S_B | S_C,                     // 7
    S_A | S_B | S_C | S_D | S_E | S_F | S_G, // 8
    S_A | S_B | S_C | S_F | S_G,          // 9
    S_A | S_B | S_C | S_E | S_F | S_G,    // A  10
    S_C | S_D | S_E | S_F | S_G,          // B
    S_A | S_D | S_E | S_F,                // C
    S_B | S_C | S_D | S_E | S_G,          // D
    S_A | S_D | S_E | S_F | S_G,          // E
    S_A | S_E | S_F | S_G,                // F
    S_A | S_C | S_D | S_E | S_F | S_G,    // G
    S_B | S_C | S_E | S_F | S_G,          // H
    S_E | S_F,                            // I
    S_B | S_C | S_D | S_E,                // J
    0,                                    // no K
    S_D | S_E | S_F,                      // L
    0,                                    // no M
    S_C | S_E | S_G,                      // n
    S_A | S_B | S_C | S_D | S_E | S_F,    // O
    S_A | S_B | S_E | S_F | S_G,          // P
    S_A | S_B | S_C | S_F | S_G,          // q
    S_E | S_G,                            // r
    S_A | S_C | S_D | S_F | S_G,          // S
    S_A | S_B | S_C,                      // T
    S_B | S_C | S_D | S_E | S_F,          // U
    0,                                    // no V
    0,                                    // no W
    0,                                    // no X
    S_B | S_C | S_F | S_G,                // y
    S_A | S_B | S_G | S_E | S_D,          // Z
    S_DP                                  // . 36
  };
  this->strobe = strobe;
  this->data = data;
  this->clock = clock;
  pinMode(strobe, OUTPUT);
  pinMode(data, OUTPUT);
  pinMode(clock, OUTPUT);
  shiftOut(this->data, this->clock, MSBFIRST, 0);
}

void ShiftSevenSeg::shiftBits(uint8_t bits){
  shiftOut(this->data, this->clock, MSBFIRST, bits);
  digitalWrite(this->strobe, HIGH);
  // need to delay 200ns here
  delayMicroseconds(1);
  digitalWrite(this->strobe, LOW);
  cur = bits;
}

void ShiftSevenSeg::showHexDigit(uint8_t digit){
  if (digit >= 16){
    return;
  }
  shiftBits(segments[digit]);
}

void ShiftSevenSeg::showAscii(char c){
    // numbers
    if (c >= 0x30 && c <= 0x39){
        shiftBits(segments[c - 0x30]);

    // uppercase letters
    }else if (c >= 'A' && c <= 'Z'){
        shiftBits(segments[c - 0x41 + 10]);

    // lowercase letters
    }else if (c >= 'a' && c <= 'z'){
        shiftBits(segments[c - 'a' + 10]);
    }else {
        shiftBits(0);
    }
}

void ShiftSevenSeg::showString(char* c){
    showString(c, 300);
}

void ShiftSevenSeg::showString(char* c, int delay_ms){
    uint8_t len = strlen(c);
    for (uint8_t i = 0; i < len; i++){
        showAscii(c[i]);
        delay(delay_ms);
    }
}

void ShiftSevenSeg::allOn(){
    shiftBits(255);
}

void ShiftSevenSeg::setDecimalPoint(bool on){
    if (on){
        cur |= this->segments[SEG_DP];
    }else{
        cur &= ~(this->segments[SEG_DP]);
    }
    shiftBits(cur);
}
