"""Explainable graph + profile recommendation scoring for ConnectBro.

The production API uses Neo4j and User Service data. This pure-Python utility
keeps the same candidate rules and TF-IDF profile similarity for local checks.
"""
from __future__ import annotations

from dataclasses import dataclass
from math import log, sqrt
import re
from typing import Any, Iterable, Mapping

try:
    import networkx as nx
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Install the graph dependency with: pip install -r requirements.txt") from exc


@dataclass(frozen=True)
class Weights:
    second_degree: float = 25
    third_degree: float = 10
    mutual_connections: float = 15
    shared_skills: float = 20
    shared_interests: float = 10
    goal_alignment: float = 15
    professional_focus: float = 5


class RecommendationEngine:
    """Rank accepted-connection graph candidates using profile content and context."""

    STOP_WORDS = {
        "a", "an", "and", "are", "at", "be", "for", "from", "in", "into", "is", "of", "on", "or", "the", "to", "with",
        "looking", "seeking", "want", "wants", "work", "working", "professional", "experience", "team", "teams", "role", "roles",
    }
    ALIASES = {
        "ml": "machine learning", "ai": "artificial intelligence", "ux": "user experience",
        "ui": "user interface", "llm": "large language model", "genai": "generative artificial intelligence",
        "js": "javascript", "ts": "typescript", "mentorship": "mentor", "mentoring": "mentor",
        "collaboration": "collaborate", "collaborating": "collaborate",
    }

    def __init__(self, friendships: Iterable[tuple[Any, Any]] | None = None,
                 profiles: Mapping[Any, Mapping[str, Any]] | None = None,
                 pending_outgoing: Iterable[tuple[Any, Any]] | None = None,
                 weights: Weights | None = None):
        self.graph = nx.Graph()
        if friendships:
            self.graph.add_edges_from(friendships)
        self.profiles = dict(profiles or {})
        self.pending_outgoing = {tuple(edge) for edge in (pending_outgoing or ())}
        self.weights = weights or Weights()

    def add_friendship(self, user_a: Any, user_b: Any) -> None:
        if user_a != user_b:
            self.graph.add_edge(user_a, user_b)

    def _degrees(self, user_id: Any) -> tuple[set[Any], set[Any], set[Any]]:
        if user_id not in self.graph:
            return set(), set(), set()
        first = set(self.graph.neighbors(user_id))
        second = {n for friend in first for n in self.graph.neighbors(friend)} - first - {user_id}
        third = {n for contact in second for n in self.graph.neighbors(contact)} - first - second - {user_id}
        return first, second, third

    def get_second_degree_recommendations(self, user_id: Any) -> list[Any]:
        return sorted(self._degrees(user_id)[1], key=str)

    def get_third_degree_recommendations(self, user_id: Any) -> list[Any]:
        return sorted(self._degrees(user_id)[2], key=str)

    @classmethod
    def _tags(cls, profile: Mapping[str, Any], key: str) -> dict[str, str]:
        raw = profile.get(key) or ()
        if isinstance(raw, str):
            raw = raw.split(",")
        tags = {}
        for value in raw:
            value = str(value).strip()
            if value:
                tags.setdefault(value.casefold(), value)
        return tags

    @classmethod
    def _tokens(cls, value: Any) -> set[str]:
        if isinstance(value, (list, tuple, set)):
            value = ", ".join(str(item) for item in value)
        result: set[str] = set()
        for word in re.findall(r"[a-z0-9+#.]+", str(value or "").casefold()):
            expanded = cls.ALIASES.get(word, word).split()
            result.update(term for term in expanded if term not in cls.STOP_WORDS)
        return result

    @classmethod
    def _cosine(cls, left: str | None, right: str | None, corpus: list[str | None]) -> float:
        a, b = cls._tokens(left), cls._tokens(right)
        if not a or not b:
            return 0.0
        doc_count = max(1, len(corpus))
        vocabulary = a | b
        idf = {term: log((doc_count + 1) / (sum(term in cls._tokens(doc) for doc in corpus) + 1)) + 1
               for term in vocabulary}
        dot = sum(idf[t] ** 2 for t in a & b)
        norm_a = sqrt(sum(idf[t] ** 2 for t in a))
        norm_b = sqrt(sum(idf[t] ** 2 for t in b))
        return dot / (norm_a * norm_b) if norm_a and norm_b else 0.0

    @staticmethod
    def _percent(value: float) -> int:
        return max(0, min(100, round(value * 100)))

    def get_recommendations(self, user_id: Any, page: int = 0, size: int = 20) -> dict[str, Any]:
        if page < 0 or size < 1 or size > 100:
            raise ValueError("page must be non-negative and size must be between 1 and 100")
        first, second, third = self._degrees(user_id)
        source = self.profiles.get(user_id, {})
        candidates = {candidate: 2 for candidate in second}
        for candidate in third:
            candidates.setdefault(candidate, 3)
        candidates = {candidate: degree for candidate, degree in candidates.items()
                      if candidate != user_id and candidate not in first
                      and (user_id, candidate) not in self.pending_outgoing}
        corpus = [source, *(self.profiles.get(candidate, {}) for candidate in candidates)]
        w = self.weights
        maximum = w.second_degree + w.mutual_connections + w.shared_skills + w.shared_interests + w.goal_alignment + w.professional_focus
        ranked = []

        for candidate, degree in candidates.items():
            profile = self.profiles.get(candidate, {})
            shared_skills = sorted((self._tags(source, "skills").keys() & self._tags(profile, "skills").keys()), key=str.casefold)
            shared_interests = sorted((self._tags(source, "interests").keys() & self._tags(profile, "interests").keys()), key=str.casefold)
            shared_goals = sorted((self._tags(source, "goals").keys() & self._tags(profile, "goals").keys()), key=str.casefold)
            skill_fit = self._cosine(source.get("skills"), profile.get("skills"), [p.get("skills") for p in corpus])
            interest_fit = self._cosine(source.get("interests"), profile.get("interests"), [p.get("interests") for p in corpus])
            goal_fit = self._cosine(source.get("goals"), profile.get("goals"), [p.get("goals") for p in corpus])
            professional_fit = self._cosine(source.get("headline"), profile.get("headline"), [p.get("headline") for p in corpus])
            mutual = len(first & set(self.graph.neighbors(candidate)))
            raw = (w.second_degree if degree == 2 else w.third_degree)
            raw += min(mutual, 5) / 5 * w.mutual_connections
            raw += skill_fit * w.shared_skills + interest_fit * w.shared_interests
            raw += goal_fit * w.goal_alignment + professional_fit * w.professional_focus
            reasons = []
            if mutual:
                reasons.append(f"You have {mutual} mutual connection{'s' if mutual != 1 else ''}.")
            reasons.append("Connected through your second-degree network." if degree == 2 else "Connected through your extended professional network.")
            if shared_skills:
                reasons.append(f"Shared skills: {', '.join(self._tags(source, 'skills')[tag] for tag in shared_skills)}.")
            elif skill_fit >= .35:
                reasons.append(f"Your skill profiles are similar ({self._percent(skill_fit)}%).")
            if shared_interests:
                reasons.append(f"Shared interests: {', '.join(self._tags(source, 'interests')[tag] for tag in shared_interests)}.")
            elif interest_fit >= .35:
                reasons.append("You have similar professional interests.")
            if shared_goals:
                reasons.append(f"Aligned goals: {', '.join(self._tags(source, 'goals')[tag] for tag in shared_goals)}.")
            elif goal_fit >= .3:
                reasons.append("Your current professional goals align.")
            if professional_fit >= .35:
                reasons.append("You have a similar professional focus.")
            ranked.append({
                "userId": candidate, "name": profile.get("name"), "headline": profile.get("headline"),
                "profilePicture": profile.get("profilePicture"), "mutualConnections": mutual,
                "sharedSkills": [self._tags(source, "skills")[tag] for tag in shared_skills],
                "sharedInterests": [self._tags(source, "interests")[tag] for tag in shared_interests],
                "sharedGoals": [self._tags(source, "goals")[tag] for tag in shared_goals], "degree": degree,
                "score": max(0, min(100, round(raw / maximum * 100))) if maximum > 0 else 0,
                "skillSimilarity": self._percent(skill_fit), "interestSimilarity": self._percent(interest_fit),
                "goalAlignment": self._percent(goal_fit), "professionalSimilarity": self._percent(professional_fit),
                "reasons": reasons,
            })
        ranked.sort(key=lambda item: (-item["score"], item["degree"], str(item["userId"])))
        start = page * size
        return {"items": ranked[start:start + size], "page": page, "size": size, "total": len(ranked)}
