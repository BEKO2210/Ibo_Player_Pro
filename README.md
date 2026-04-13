<div align="center"><img src="./assets/logo/logo-no_background.png" alt="Premium TV Player Logo" width="140" />
  
  Premium TV Player

---

A premium multi-platform TV player built Android TV first

<p align="center">
  <img src="https://img.shields.io/badge/Android%20TV-First-0A84FF?style=for-the-badge&logo=android&logoColor=white" alt="Android TV First" />
  <img src="https://img.shields.io/badge/Status-Private%20Development-111827?style=for-the-badge" alt="Private Development" />
  <img src="https://img.shields.io/badge/Trial-14%20Days-22C55E?style=for-the-badge" alt="14 Day Trial" />
  <img src="https://img.shields.io/badge/Profiles-Up%20to%205-8B5CF6?style=for-the-badge" alt="Up to 5 Profiles" />
  <img src="https://img.shields.io/badge/Family%20Plan-Enabled-06B6D4?style=for-the-badge" alt="Family Plan" />
  <img src="https://img.shields.io/badge/Cloud%20Sync-Built%20In-2563EB?style=for-the-badge" alt="Cloud Sync" />
  <img src="https://img.shields.io/badge/License-All%20Rights%20Reserved-DC2626?style=for-the-badge" alt="All Rights Reserved" />
</p><p align="center">
  Premium dark design. TV-first navigation. Account-based entitlements. Server-authoritative access.
</p></div>

---

Vision

Premium TV Player is being built as a serious premium living-room product, not just a functional player.

The goal is a polished, cinematic, multi-platform experience with elegant TV navigation, premium focus states, strong account infrastructure, and a backend that treats entitlements, devices, profiles, and sync as first-class product features.

This project is designed around a simple idea:

> A TV player should feel as premium as the content experience around it.




---

What this project is aiming for

Android TV first with a true 10-foot experience

Premium dark interface inspired by high-end streaming products

Account-based entitlements, not device hacks

14-day server-side trial

Up to 5 profiles

Family plan support

Cloud sync for playback state, favorites, and profiles

User-authorized sources only — the app ships empty

Scalable architecture across TV, mobile, web, and future clients



---

Why this is different

Most TV players stop at basic playback.

This project is being built around a much higher standard:

premium visual language

TV-native focus and navigation behavior

backend-authoritative entitlement logic

clean architecture from the beginning

platform expansion planned from day one

shared design tokens and structured delivery runs

product-level thinking instead of quick prototype shortcuts


This is meant to become a real product.


---

Core product pillars

Pillar	Description

Premium UI	Dark, elegant, cinematic, and built for large screens first.
TV-first UX	Focus behavior, spacing, typography, and interaction patterns designed for remote navigation.
Account-based system	Entitlements, profiles, and device slots belong to the account, not to MAC-address hacks.
Server authority	Trial state, active purchases, revocations, and limits are enforced on the backend.
Cloud sync	Watch state, favorites, and profile data are designed to move with the user.
Multi-platform roadmap	Android TV first, then Android mobile, admin web, tvOS/iOS, Samsung Tizen, and LG webOS.
User-controlled sources	The app ships empty; users add their own M3U / M3U8 / XMLTV sources.



---

Planned experience

Welcome → Sign up / Log in → Start trial → Pick profile → Home → Playback → Continue watching → Sync across devices

The intended product experience includes:

cinematic onboarding

premium hero sections and content rows

profile picker

continue watching

favorites

parental controls

EPG integration

live and VOD playback

purchase and restore flows

synced account state across supported clients



---

Platform strategy

The long-term rollout is planned in this order:

1. Android TV


2. Android Mobile


3. Admin Web


4. tvOS / iOS


5. Samsung Tizen


6. LG webOS



Android TV is the current primary client and the first premium experience being built.


---

Product model

Area	Direction

Positioning	Neutral premium player for user-authorized sources
Auth	Firebase Authentication with own backend entitlement layer
Monetization	14-day trial, Lifetime Single, Lifetime Family
Profiles	Up to 5 profiles
Device model	Server-managed device slots tied to account
Playback data	Cloud-synced state and local caching
Source model	User adds own M3U / M3U8 / XMLTV sources
License	Proprietary, all rights reserved



---

Architecture at a glance

Client

Kotlin

Jetpack Compose

Compose for TV

Media3 / ExoPlayer

Hilt

Navigation-Compose

Room

DataStore


Backend

NestJS

PostgreSQL

Prisma

Redis

Firebase Admin

REST API + structured contracts


Shared packages

API contracts

Domain models

Parsers

i18n

UI tokens

Entitlement engine



---

Development status

> Private repository
Early development in progress



Current work includes:

backend foundation

auth and entitlement modules

billing worker

profile and source modules

Android TV bootstrap

premium Compose design system

onboarding and auth flow implementation


This repository is under active structured development.


---

Repository guide

For contributors and coding agents, start with:

CLAUDE.md

CLAUDE.md is the operational guide for this repository and contains:

current project state

locked product decisions

architecture direction

active run plan

roadmap progression

execution protocol

implementation guardrails


Anyone working on this repository should read that file first.


---

Project principles

Build for the living-room experience first

Prefer clean architecture over fast shortcuts

Keep entitlement and billing logic server-authoritative

Avoid scope creep by working in structured runs

Maintain a premium design language across every screen

Prepare for multi-platform expansion without compromising V1 quality



---

License

Copyright © 2026 Premium TV Player. All Rights Reserved.

See LICENSE.

This repository and all of its contents are proprietary and confidential.
No part of this codebase may be copied, modified, published, distributed, sublicensed, or sold without prior written permission from the copyright holder.

Unauthorized use, reproduction, or distribution is strictly prohibited.
