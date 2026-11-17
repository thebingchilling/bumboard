"""Lychee's handwriting recogniser: the ink's points (ink.py) through a few 1-D convolutions
(the local shape of each stroke), then two bidirectional LSTM layers (the strokes in their
order, and how they sit together), pooled over the whole character into one class per character.
The design follows Google's published online handwriting work (Carbune et al., "Fast
multi-language LSTM-based online handwriting recognition", 2020): the pen's path, not a picture.
"""

from __future__ import annotations

import torch
from torch import nn

import ink


def _block(cin: int, cout: int, stride: int) -> nn.Sequential:
    return nn.Sequential(
        nn.Conv1d(cin, cout, 5, stride=stride, padding=2), nn.BatchNorm1d(cout), nn.ReLU(),
        nn.Conv1d(cout, cout, 3, padding=1), nn.BatchNorm1d(cout), nn.ReLU(),
    )


class Recogniser(nn.Module):
    def __init__(self, classes: int, hidden: int = 160, embed: int = 384):
        super().__init__()
        # each step halves the points: 256 become 32 by the LSTM
        self.conv = nn.Sequential(
            _block(ink.FEATURES, 64, 1),
            _block(64, 96, 2),
            _block(96, 160, 2),
            _block(160, 224, 2),
        )
        self.lstm = nn.LSTM(224, hidden, num_layers=1, bidirectional=True, batch_first=True)
        self.head = nn.Sequential(
            nn.Linear(4 * hidden, embed), nn.ReLU(), nn.Dropout(0.2),
            nn.Linear(embed, classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        """x: [batch, points, 6] -> logits [batch, classes]."""
        h = self.conv(x.transpose(1, 2)).transpose(1, 2)
        h, _ = self.lstm(h)
        pooled = torch.cat([h.mean(dim=1), h.amax(dim=1)], dim=1)
        return self.head(pooled)
