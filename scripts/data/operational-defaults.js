/* Autor secundário: Pedro Henrique Contardi Soler - RA: 25005592 */
window.AgroOperationalDefaults = {
  "plots": [
    {
      "id": "plots-0",
      "plot": "T-01 Norte",
      "crop": "soja",
      "hectares": 124,
      "status": "Ativo"
    },
    {
      "id": "plots-1",
      "plot": "T-02 Sul",
      "crop": "milho",
      "hectares": 98,
      "status": "Irrigação pendente"
    },
    {
      "id": "plots-2",
      "plot": "T-03 Leste",
      "crop": "soja",
      "hectares": 156,
      "status": "Ativo"
    },
    {
      "id": "plots-3",
      "plot": "T-04 Oeste",
      "crop": "trigo",
      "hectares": 72,
      "status": "Em preparo"
    },
    {
      "id": "plots-4",
      "plot": "T-05 Centro",
      "crop": "soja",
      "hectares": 110,
      "status": "Ativo"
    }
  ],
  "activities": [
    {
      "id": "activities-0",
      "activity": "Aplicação de fungicida",
      "tipo": "Aplicação",
      "talhao": "Talhão A3",
      "date": "08 ago",
      "status": "Pendente"
    },
    {
      "id": "activities-1",
      "activity": "Calagem do solo",
      "tipo": "Manutenção",
      "talhao": "Talhão D1",
      "date": "09 ago",
      "status": "Pendente"
    },
    {
      "id": "activities-2",
      "activity": "Irrigação por gotejamento",
      "tipo": "Irrigação",
      "talhao": "Talhão C2",
      "date": "06 ago",
      "status": "Agendada"
    },
    {
      "id": "activities-3",
      "activity": "Análise foliar",
      "tipo": "Aplicação",
      "talhao": "Talhão B4",
      "date": "07 ago",
      "status": "Agendada"
    },
    {
      "id": "activities-4",
      "activity": "Colheita parcial soja",
      "tipo": "Colheita",
      "talhao": "Talhão B1",
      "date": "05 ago",
      "status": "Em andamento"
    },
    {
      "id": "activities-5",
      "activity": "Manutenção trator JD",
      "tipo": "Manutenção",
      "talhao": "Oficina",
      "date": "04 ago",
      "status": "Em andamento"
    },
    {
      "id": "activities-6",
      "activity": "Aplicação herbicida",
      "tipo": "Aplicação",
      "talhao": "Talhão A1",
      "date": "03 ago",
      "status": "Concluída"
    },
    {
      "id": "activities-7",
      "activity": "Irrigação pivô central",
      "tipo": "Irrigação",
      "talhao": "Talhão C1",
      "date": "02 ago",
      "status": "Concluída"
    }
  ],
  "stock": [
    {
      "id": "stock-0",
      "product": "Glifosato 480 SL",
      "category": "herbicida",
      "qty": 85,
      "unit": "L",
      "min": 200,
      "movements": []
    },
    {
      "id": "stock-1",
      "product": "Ureia 45% N",
      "category": "fertilizante",
      "qty": 320,
      "unit": "kg",
      "min": 500,
      "movements": []
    },
    {
      "id": "stock-2",
      "product": "Semente Soja RR",
      "category": "semente",
      "qty": 42,
      "unit": "sc",
      "min": 80,
      "movements": []
    },
    {
      "id": "stock-3",
      "product": "Herbicida 2,4-D",
      "category": "herbicida",
      "qty": 156,
      "unit": "L",
      "min": 100,
      "movements": []
    },
    {
      "id": "stock-4",
      "product": "Fungicida Azoxistrobina",
      "category": "fungicida",
      "qty": 28,
      "unit": "L",
      "min": 50,
      "movements": []
    },
    {
      "id": "stock-5",
      "product": "MAP 11-52-00",
      "category": "fertilizante",
      "qty": 1240,
      "unit": "kg",
      "min": 800,
      "movements": []
    },
    {
      "id": "stock-6",
      "product": "Inseticida Lambda",
      "category": "inseticida",
      "qty": 64,
      "unit": "L",
      "min": 40,
      "movements": []
    }
  ],
  "occurrences": [
    {
      "id": "occurrences-0",
      "occ": "Infestação de lagarta-do-cartucho",
      "tipo": "Praga",
      "talhao": "Talhão B2",
      "date": "05 ago",
      "severity": "Alta",
      "status": "Aberta",
      "timeline": [
        {
          "time": "05 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Praga identificado(a) em Talhão B2."
        }
      ]
    },
    {
      "id": "occurrences-1",
      "occ": "Chuva excessiva — 85mm em 24h",
      "tipo": "Clima",
      "talhao": "Talhão A1",
      "date": "04 ago",
      "severity": "Média",
      "status": "Em análise",
      "timeline": [
        {
          "time": "04 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Clima identificado(a) em Talhão A1."
        }
      ]
    },
    {
      "id": "occurrences-2",
      "occ": "Falha no sistema de irrigação",
      "tipo": "Equipamento",
      "talhao": "Talhão C4",
      "date": "03 ago",
      "severity": "Alta",
      "status": "Aberta",
      "timeline": [
        {
          "time": "03 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Equipamento identificado(a) em Talhão C4."
        }
      ]
    },
    {
      "id": "occurrences-3",
      "occ": "Mancha-alvo na soja",
      "tipo": "Doença",
      "talhao": "Talhão A3",
      "date": "01 ago",
      "severity": "Média",
      "status": "Em análise",
      "timeline": [
        {
          "time": "01 ago · registro",
          "title": "Ocorrência aberta",
          "desc": "Doença identificado(a) em Talhão A3."
        }
      ]
    },
    {
      "id": "occurrences-4",
      "occ": "Geada leve registrada",
      "tipo": "Clima",
      "talhao": "Talhão D1",
      "date": "28 jul",
      "severity": "Baixa",
      "status": "Resolvida",
      "timeline": [
        {
          "time": "28 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Clima identificado(a) em Talhão D1."
        }
      ]
    },
    {
      "id": "occurrences-5",
      "occ": "Percevejo-marrom detectado",
      "tipo": "Praga",
      "talhao": "Talhão B4",
      "date": "26 jul",
      "severity": "Média",
      "status": "Resolvida",
      "timeline": [
        {
          "time": "26 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Praga identificado(a) em Talhão B4."
        }
      ]
    },
    {
      "id": "occurrences-6",
      "occ": "Trator MF 4275 — superaquecimento",
      "tipo": "Equipamento",
      "talhao": "Oficina",
      "date": "24 jul",
      "severity": "Alta",
      "status": "Resolvida",
      "timeline": [
        {
          "time": "24 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Equipamento identificado(a) em Oficina."
        }
      ]
    },
    {
      "id": "occurrences-7",
      "occ": "Ferrugem asiática — início",
      "tipo": "Doença",
      "talhao": "Talhão C2",
      "date": "22 jul",
      "severity": "Média",
      "status": "Em análise",
      "timeline": [
        {
          "time": "22 jul · registro",
          "title": "Ocorrência aberta",
          "desc": "Doença identificado(a) em Talhão C2."
        }
      ]
    }
  ],
  "costs": {
    "budget": 520000,
    "period": "Jan — Ago 2026",
    "entries": [
      { "id": 1, "description": "Glifosato 20L", "category": "Insumos", "plot": "T-03 Leste", "date": "2026-08-05", "value": 2840 },
      { "id": 2, "description": "Salário operador — Jul", "category": "Mão de Obra", "plot": "Propriedade", "date": "2026-08-01", "value": 4200 },
      { "id": 3, "description": "Manutenção pulverizador", "category": "Maquinário", "plot": "T-01 Norte", "date": "2026-07-28", "value": 1650 },
      { "id": 4, "description": "Diesel — 500 L", "category": "Combustível", "plot": "Propriedade", "date": "2026-07-25", "value": 3100 },
      { "id": 5, "description": "Sementes de soja — 2 t", "category": "Insumos", "plot": "T-01 Norte", "date": "2026-07-20", "value": 8400 }
    ]
  }
};
// Os mesmos valores iniciais das configurações também ficam disponíveis
// para outras telas calcularem indicadores antes do primeiro salvamento.
window.AgroOperationalDefaults.settings = {
  profile: {name: "João Silva", email: "joao@fazendasantarita.com", phone: "(19) 99999-1234", role: "Produtor rural"},
  property: {farm: "Fazenda Santa Rita", area: 1284, city: "Campinas, SP", harvest: "2025/26"},
  preferences: {stock: true, weather: true, activities: true, occurrences: true, reports: false}
};
