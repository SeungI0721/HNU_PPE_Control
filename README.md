# Smart Shield
evelopment of a PPE Wearable-Based Hazard Detection System for Construction Site Safety

---

## 📌 Overview

작업자의 **생체 신호, 환경 데이터, 행동 패턴**을 통합 분석하여
열사병 및 이상 상태를 사전에 감지하고 즉각적인 경고를 제공하는 시스템입니다.

기존의 환경 중심 안전 관리 방식의 한계를 보완하여,
**작업자 개인 상태 기반 실시간 위험 판단 + 물리적 제어 시스템**을 구현합니다.

---

## 🎯 Key Features

### 1. 실시간 데이터 수집 (ESP32)

* 환경: 온도, 습도 (BME280)
* 생체: 심박수, 산소포화도 (MAX30102), 피부 온도 (MLX90614)
* 행동: 움직임 및 자세 (MPU6050)

→ ESP32에서 센서 데이터를 수집한 뒤 BLE로 앱에 전송

---

### 2. 위험도 분석 (Android App)

* 환경 + 생체 + 행동 데이터 통합 분석
* 규칙 기반 위험 점수 계산

→ **정상 / 주의 / 위험 / 응급 단계로 분류**

---

### 3. 양방향 제어 시스템

단순 모니터링이 아니라 **앱에서 위험도를 계산한 뒤, 결과를 다시 ESP32로 보내 LED·진동·음성 출력을 제어하는 구조**입니다.

---

### 4. 즉각적인 경고 시스템

**ESP32에서 실행**

* LED 상태 표시
* 진동 모터
* 음성 출력 (DFPlayer Mini)

**앱에서 실행**

* 위험 단계 UI 표시
* 경고 팝업
* 스마트폰 진동

---

## 🧠 System Architecture

```text
[ Sensor Layer ]
환경 / 생체 / 행동 센서
        ↓
[ ESP32 ]
데이터 수집 및 BLE 송신
        ↓
[ BLE 통신 ]
(Notify / Write 양방향)
        ↓
[ Android App ]
위험도 계산 + UI 표시
        ↓
[ ESP32 ]
경고 장치 제어 (LED / 진동 / 음성)
```

---

## 🔄 Communication Structure (BLE)

### Service UUID

```text
0000FFF0-0000-1000-8000-00805F9B34FB
```

---

### 1. Sensor Data Characteristic

```text
UUID: 0000FFF1-0000-1000-8000-00805F9B34FB
Direction: ESP32 → App
Property: Notify
```

데이터 형식:

```text
ID:0001,TEMP:36.5,HR:102,ENV:33.1,HUM:71,POSTURE:NORMAL
```

---

### 2. Control Command Characteristic

```text
UUID: 0000FFF2-0000-1000-8000-00805F9B34FB
Direction: App → ESP32
Property: Write
```

전송 데이터:

```text
RISK:SAFE
RISK:CAUTION
RISK:DANGER
RISK:EMERGENCY
```

---

## 📱 Android Application

작업자용 앱 기능:

* BLE 장치 스캔 및 연결
* 센서 데이터 실시간 표시
* 위험도 계산
* 상태 색상 UI (초록 / 노랑 / 빨강)
* 경고 팝업 및 진동
* ESP32로 제어 명령 전송

---

## ⚙️ Hardware Components

| Component       | Description      |
| --------------- | ---------------- |
| ESP32           | 메인 컨트롤러 + BLE 통신 |
| BME280          | 온도, 습도 측정        |
| MLX90614        | 피부 온도 측정         |
| MAX30102        | 심박수 및 SpO₂       |
| MPU6050         | 움직임 / 자세 감지      |
| DFPlayer Mini   | 음성 출력            |
| Vibration Motor | 진동 경고            |
| LED             | 상태 시각화           |

---

## 🔍 Core Logic

1. ESP32 → 센서 데이터 수집
2. BLE를 통해 앱으로 전송
3. 앱에서 위험도 계산
4. 위험 단계 분류
5. UI 및 사용자 경고
6. 계산 결과를 ESP32로 전송
7. ESP32가 물리적 경고 실행

---

## 🚧 Problem & Motivation

* 건설 현장은 고온·고습 환경
* 기존 시스템은

  * 환경 기준 중심
  * 개인 상태 반영 부족
  * 사후 대응 중심

→ **개인 맞춤형 실시간 위험 감지 + 즉각적 제어 시스템 필요**

---

## 📈 Expected Impact

* 온열질환 사전 예방
* 산업재해 감소
* 작업자 안전성 향상
* 스마트 PPE 기술 확장 가능

---

## 🛠 Tech Stack

* Android (Kotlin)
* ESP32 (?)
* BLE (GATT 기반 통신)
* Sensor Fusion
* Rule-based Risk Evaluation

---

## 📅 Development Roadmap

1. 센서 통합 및 하드웨어 구성
2. BLE 통신 구현 (양방향)
3. 위험도 알고리즘 개발
4. Android 앱 연동
5. ESP32 제어 로직 구현
6. 통합 테스트 및 검증

---

## 🚀 Future Work

* 관리자 모니터링 시스템
* 클라우드 데이터 저장
