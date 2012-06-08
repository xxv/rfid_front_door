#include "Arduino.h"
#include "ShiftSevenSeg.h"

#define SEG_DP 36

// the number of steps in the animation
#define FADE_STEPS 20
#define FADE_MULT 11
#define MIN_BRIGHTNESS 20

ShiftSevenSeg::ShiftSevenSeg(uint8_t strobe, uint8_t data, uint8_t clock, uint8_t output_enable) : strobe(strobe), data(data), clock(clock), output_enable(output_enable){
  pinMode(strobe, OUTPUT);
  pinMode(data, OUTPUT);
  pinMode(clock, OUTPUT);
  pinMode(output_enable, OUTPUT);
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
        for (uint8_t j = 0; j < FADE_STEPS; j++){
            delay(delay_ms/FADE_STEPS);
            analogWrite(this->output_enable, 255 - j * FADE_MULT);
        }
    }
    digitalWrite(this->output_enable, HIGH);
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
