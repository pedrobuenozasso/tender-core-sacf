<script setup>
import { computed, reactive, ref } from 'vue'

const today = new Date()
const lastWeek = new Date(today)
lastWeek.setDate(today.getDate() - 7)

const PRESETS = [
  { label: 'Hoje', days: 0 },
  { label: '7 dias', days: 7 },
  { label: '15 dias', days: 15 },
  { label: '30 dias', days: 30 },
]

const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const ENABLE_DEMO_FALLBACK = import.meta.env.VITE_ENABLE_DEMO_FALLBACK === 'true'

const UF_OPTIONS = [
  'AC', 'AL', 'AP', 'AM', 'BA', 'CE', 'DF', 'ES', 'GO',
  'MA', 'MT', 'MS', 'MG', 'PA', 'PB', 'PR', 'PE', 'PI',
  'RJ', 'RN', 'RS', 'RO', 'RR', 'SC', 'SP', 'SE', 'TO',
]

const form = reactive({
  mode: 'open',
  startDate: formatDateInput(lastWeek),
  endDate: formatDateInput(today),
  state: '',
})

const keywordItems = ref([
  { id: 1, text: 'software', type: 'monitored', active: true },
  { id: 2, text: 'tecnologia', type: 'monitored', active: true },
  { id: 3, text: 'licença', type: 'monitored', active: true },
  { id: 4, text: 'show', type: 'blocked', active: true },
  { id: 5, text: 'festa', type: 'blocked', active: true },
])

const loading = ref(false)
const error = ref('')
const response = ref(null)
const selected = ref(null)
const activeView = ref(typeof window !== 'undefined' && window.location.hash === '#keywords' ? 'keywords' : 'search')
const keywordTab = ref('monitored')
const newKeyword = ref('')
const keywordError = ref('')
const filterUf = ref('')
const filterText = ref('')
const demoMode = ref(false)

const results = computed(() => response.value?.results || [])
const filtered = computed(() => {
  return results.value.filter((item) => {
    if (filterUf.value && item.state !== filterUf.value) return false
    if (!filterText.value) return true

    const query = normalize(filterText.value)
    return [
      item.object,
      item.agency,
      item.supplier,
      item.supplierDocument,
      item.modality,
      ...(item.matchedKeywords || []),
    ].some((value) => normalize(String(value || '')).includes(query))
  })
})

const total = computed(() => response.value?.total || results.value.length)
const states = computed(() => {
  return [...new Set(results.value.map((item) => item.state).filter(Boolean))].sort()
})
const topStates = computed(() => {
  const map = filtered.value.reduce((acc, item) => {
    if (item.state) acc[item.state] = (acc[item.state] || 0) + 1
    return acc
  }, {})

  return Object.entries(map).sort((a, b) => b[1] - a[1]).slice(0, 8)
})
const totalValue = computed(() => {
  return filtered.value.reduce((sum, item) => sum + (Number(item.estimatedValue) || 0), 0)
})
const monitoredKeywords = computed(() => keywordItems.value.filter(item => item.type === 'monitored'))
const blockedKeywords = computed(() => keywordItems.value.filter(item => item.type === 'blocked'))
const currentKeywords = computed(() => keywordTab.value === 'blocked' ? blockedKeywords.value : monitoredKeywords.value)
const activeMonitoredCount = computed(() => monitoredKeywords.value.filter(item => item.active).length)
const activeBlockedCount = computed(() => blockedKeywords.value.filter(item => item.active).length)
const modeLabel = computed(() => form.mode === 'awarded' ? 'Ganhadores' : 'Licitações')
const modeDescription = computed(() => {
  return form.mode === 'awarded'
    ? 'Contratos e fornecedores vencedores localizados no PNCP.'
    : 'Editais abertos localizados no PNCP por período e estado.'
})

async function search() {
  loading.value = true
  error.value = ''
  response.value = null
  selected.value = null
  demoMode.value = false

  try {
    const res = await fetch(`${API_BASE_URL}/api/tenders/search`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        mode: form.mode,
        startDate: form.startDate,
        endDate: form.endDate,
        keywords: monitoredKeywords.value
          .filter(item => item.active)
          .map(item => ({ text: item.text, qualifiers: [] })),
        blockedKeywords: blockedKeywords.value
          .filter(item => item.active)
          .map(item => item.text),
        states: form.state ? [form.state] : [],
        maxResults: 100,
      }),
    })

    const data = await res.json().catch(() => ({}))
    if (!res.ok) throw new Error(data.error || 'Nao foi possivel consultar o PNCP.')
    response.value = data
  } catch (err) {
    if (ENABLE_DEMO_FALLBACK) {
      demoMode.value = true
      error.value = 'Backend Java indisponivel nesta visualizacao. Exibindo dados demonstrativos.'
      response.value = buildDemoResponse()
    } else {
      error.value = err?.message || 'Nao foi possivel consultar o PNCP.'
    }
  } finally {
    loading.value = false
  }
}

function applyPreset(days) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days)
  form.startDate = formatDateInput(start)
  form.endDate = formatDateInput(end)
}

function addKeyword() {
  const text = newKeyword.value.trim()
  keywordError.value = ''
  if (!text) return

  const type = keywordTab.value === 'blocked' ? 'blocked' : 'monitored'
  const exists = keywordItems.value.some((item) => (
    item.type === type && normalize(item.text) === normalize(text)
  ))

  if (exists) {
    keywordError.value = 'Essa keyword ja esta cadastrada.'
    return
  }

  keywordItems.value.unshift({
    id: Date.now(),
    text,
    type,
    active: true,
  })
  newKeyword.value = ''
}

function deleteKeyword(id) {
  keywordItems.value = keywordItems.value.filter(item => item.id !== id)
}

function toggleKeyword(id) {
  const keyword = keywordItems.value.find(item => item.id === id)
  if (keyword) keyword.active = !keyword.active
}

function normalize(value) {
  return value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
}

function formatDateInput(date) {
  return date.toISOString().slice(0, 10)
}

function formatDate(value) {
  if (!value) return '-'
  return String(value).slice(0, 10).split('-').reverse().join('/')
}

function formatCurrency(value) {
  if (value == null) return '-'
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
    maximumFractionDigits: 0,
  }).format(value)
}

function buildDemoResponse() {
  const demoResults = [
    {
      id: 'demo-1',
      type: form.mode,
      pncpControlNumber: '000001/2026',
      object: 'Contratacao de licencas de software, suporte tecnico e servicos de implantacao',
      agency: 'Prefeitura Municipal de Sao Paulo',
      supplier: form.mode === 'awarded' ? 'SACF Tecnologia Ltda.' : null,
      supplierDocument: form.mode === 'awarded' ? '12.345.678/0001-90' : null,
      state: 'SP',
      estimatedValue: 248000,
      publicationDate: form.endDate,
      signatureDate: form.mode === 'awarded' ? form.endDate : null,
      closingDate: form.mode === 'open' ? form.endDate : null,
      pncpLink: 'https://pncp.gov.br',
      matchedKeywords: ['software', 'licenca'],
      modality: 'Pregao Eletronico',
    },
    {
      id: 'demo-2',
      type: form.mode,
      pncpControlNumber: '000002/2026',
      object: 'Aquisicao de equipamentos de tecnologia e renovacao de plataforma digital',
      agency: 'Secretaria de Administracao',
      supplier: form.mode === 'awarded' ? 'Fornecedor Demonstrativo S/A' : null,
      supplierDocument: form.mode === 'awarded' ? '98.765.432/0001-10' : null,
      state: 'RJ',
      estimatedValue: 580000,
      publicationDate: form.startDate,
      signatureDate: form.mode === 'awarded' ? form.startDate : null,
      closingDate: form.mode === 'open' ? form.endDate : null,
      pncpLink: 'https://pncp.gov.br',
      matchedKeywords: ['tecnologia'],
      modality: 'Concorrencia',
    },
    {
      id: 'demo-3',
      type: form.mode,
      pncpControlNumber: '000003/2026',
      object: 'Servicos continuados de monitoramento, integracao e atendimento operacional',
      agency: 'Instituto Federal de Minas Gerais',
      supplier: form.mode === 'awarded' ? 'Integracao Brasil Ltda.' : null,
      supplierDocument: form.mode === 'awarded' ? '21.000.111/0001-55' : null,
      state: 'MG',
      estimatedValue: 126500,
      publicationDate: form.startDate,
      signatureDate: form.mode === 'awarded' ? form.endDate : null,
      closingDate: form.mode === 'open' ? form.endDate : null,
      pncpLink: 'https://pncp.gov.br',
      matchedKeywords: ['software', 'tecnologia'],
      modality: 'Dispensa',
    },
  ]

  return {
    mode: form.mode,
    startDate: form.startDate,
    endDate: form.endDate,
    total: demoResults.length,
    results: demoResults,
  }
}

function clearFilters() {
  filterUf.value = ''
  filterText.value = ''
}
</script>

<template>
  <div class="app-frame">
    <header class="topbar">
      <button class="topbar-brand" type="button" @click="activeView = 'search'">
        <img src="/brand/sacf-app-icon.png" alt="" />
        <span>
          <strong>SACF</strong>
          <small>Tender</small>
        </span>
      </button>
    </header>

    <section class="workspace">
      <aside class="sidebar">
      <nav class="nav">
        <button class="nav-item" :class="{ active: activeView === 'search' }" type="button" @click="activeView = 'search'">
          <span class="nav-icon">
            <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
          </span>
          Licitações
        </button>
        <button class="nav-item" :class="{ active: activeView === 'keywords' }" type="button" @click="activeView = 'keywords'">
          <span class="nav-icon">
            <svg viewBox="0 0 24 24"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><path d="M7 7h.01"/></svg>
          </span>
          Palavras-chave
        </button>
      </nav>
      </aside>

      <main class="content">
        <section class="page-heading">
          <div>
            <h1>
              {{
                activeView === 'keywords'
                    ? 'Configurar Keywords'
                    : (form.mode === 'awarded' ? 'Buscar Ganhadores' : 'Buscar Licitações')
              }}
            </h1>
            <p>
              {{
                activeView === 'keywords'
                    ? 'Gerencie as palavras monitoradas e bloqueadas no PNCP.'
                    : modeDescription
              }}
            </p>
          </div>
        </section>

        <section v-if="activeView === 'search'" class="search-card">
          <form @submit.prevent="search">
            <div class="controls-grid">
              <div class="field mode-field">
                <label>Tipo de busca</label>
                <div class="segmented">
                  <button
                    type="button"
                    :class="{ active: form.mode === 'open' }"
                    @click="form.mode = 'open'"
                  >
                    Abertas
                  </button>
                  <button
                    type="button"
                    :class="{ active: form.mode === 'awarded' }"
                    @click="form.mode = 'awarded'"
                  >
                    Ganhadores
                  </button>
                </div>
              </div>

              <div class="field preset-field">
                <label>Período</label>
                <div class="preset-row">
                  <button v-for="preset in PRESETS" :key="preset.label" type="button" @click="applyPreset(preset.days)">
                    {{ preset.label }}
                  </button>
                </div>
              </div>

              <div class="field date-field">
                <label>De</label>
                <input v-model="form.startDate" type="date" />
              </div>

              <div class="field date-field">
                <label>Até</label>
                <input v-model="form.endDate" type="date" />
              </div>

              <div class="field uf-field">
                <label>UF</label>
                <select v-model="form.state">
                  <option value="">Todos os estados</option>
                  <option v-for="uf in UF_OPTIONS" :key="uf" :value="uf">{{ uf }}</option>
                </select>
              </div>

              <button class="search-button" :disabled="loading" type="submit">
                <span v-if="loading" class="spinner" />
                <svg v-else viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
                {{ loading ? 'Buscando...' : 'Buscar' }}
              </button>
            </div>
          </form>

          <p v-if="error" class="notice" :class="{ warn: demoMode }">{{ error }}</p>
        </section>

        <section v-else-if="activeView === 'keywords'" class="keyword-panel">
          <div class="keyword-tabs">
            <button
              type="button"
              :class="{ active: keywordTab === 'monitored' }"
              @click="keywordTab = 'monitored'; keywordError = ''"
            >
              Monitoradas <span>{{ activeMonitoredCount }}</span>
            </button>
            <button
              type="button"
              class="danger"
              :class="{ active: keywordTab === 'blocked' }"
              @click="keywordTab = 'blocked'; keywordError = ''"
            >
              Proibidas <span>{{ activeBlockedCount }}</span>
            </button>
          </div>

          <form class="keyword-add-form" @submit.prevent="addKeyword">
            <input
              v-model="newKeyword"
              :placeholder="keywordTab === 'blocked' ? 'Nova palavra proibida' : 'Nova palavra monitorada'"
            />
            <button type="submit" :class="{ danger: keywordTab === 'blocked' }" :disabled="!newKeyword.trim()">
              <svg viewBox="0 0 24 24"><path d="M12 5v14"/><path d="M5 12h14"/></svg>
              Adicionar
            </button>
          </form>

          <p v-if="keywordError" class="keyword-error">{{ keywordError }}</p>

          <div class="keyword-help" :class="{ danger: keywordTab === 'blocked' }">
            <svg v-if="keywordTab === 'monitored'" viewBox="0 0 24 24"><path d="M20 6L9 17l-5-5"/></svg>
            <svg v-else viewBox="0 0 24 24"><circle cx="12" cy="12" r="10"/><path d="M12 8v4"/><path d="M12 16h.01"/></svg>
            <p v-if="keywordTab === 'monitored'">
              <strong>Monitoradas</strong> entram na busca enviada para a API Java.
            </p>
            <p v-else>
              Contratos com qualquer <strong>palavra proibida</strong> são excluídos dos resultados.
            </p>
          </div>

          <div class="keyword-list">
            <article v-for="item in currentKeywords" :key="item.id" class="keyword-row" :class="{ disabled: !item.active, danger: item.type === 'blocked' }">
              <span>{{ item.text }}</span>
              <div>
                <button class="toggle" type="button" :class="{ on: item.active }" @click="toggleKeyword(item.id)" :aria-label="item.active ? 'Desativar' : 'Ativar'">
                  <i />
                </button>
                <button class="delete-keyword" type="button" @click="deleteKeyword(item.id)" aria-label="Excluir keyword">
                  <svg viewBox="0 0 24 24"><path d="M3 6h18"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></svg>
                </button>
              </div>
            </article>

            <div v-if="currentKeywords.length === 0" class="keyword-empty">
              Nenhuma palavra cadastrada.
            </div>
          </div>
        </section>

        <section v-if="activeView === 'search' && response" class="kpi-row">
          <article class="kpi-card">
            <span>{{ modeLabel }}</span>
            <strong>{{ total }}</strong>
            <small>{{ filtered.length }} visiveis com filtros</small>
          </article>
          <article class="kpi-card blue">
            <span>{{ form.mode === 'awarded' ? 'Valor contratado' : 'Valor estimado' }}</span>
            <strong>{{ formatCurrency(totalValue) }}</strong>
            <small>Soma dos resultados filtrados</small>
          </article>
          <article class="kpi-card">
            <span>Estados</span>
            <strong>{{ states.length }}</strong>
            <small>UFs com resultados</small>
          </article>
          <article class="kpi-card">
            <span>Periodo</span>
            <strong>{{ formatDate(form.startDate) }}</strong>
            <small>ate {{ formatDate(form.endDate) }}</small>
          </article>
        </section>

        <section v-if="activeView === 'search' && response" class="results-panel">
          <div class="filters-bar">
            <span>UFs</span>
            <button
              v-for="[uf, count] in topStates"
              :key="uf"
              type="button"
              class="uf-chip"
              :class="{ active: filterUf === uf }"
              @click="filterUf = filterUf === uf ? '' : uf"
            >
              {{ uf }} <small>{{ count }}</small>
            </button>

            <div class="table-tools">
              <select v-model="filterUf">
                <option value="">Todos os estados</option>
                <option v-for="uf in states" :key="uf" :value="uf">{{ uf }}</option>
              </select>
              <input v-model="filterText" placeholder="Filtrar resultados" />
              <button v-if="filterUf || filterText" type="button" @click="clearFilters">Limpar</button>
            </div>
          </div>

          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Data</th>
                  <th>UF</th>
                  <th>{{ form.mode === 'awarded' ? 'Fornecedor' : 'Orgao' }}</th>
                  <th>Objeto</th>
                  <th>Keywords</th>
                  <th>{{ form.mode === 'awarded' ? 'Assinatura' : 'Encerramento' }}</th>
                  <th class="right">Valor</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                <tr v-if="filtered.length === 0">
                  <td colspan="8" class="empty-row">Nenhum resultado encontrado.</td>
                </tr>
                <tr v-for="item in filtered" :key="item.id || item.pncpControlNumber" @click="selected = item">
                  <td>{{ formatDate(item.publicationDate) }}</td>
                  <td><span class="state-pill">{{ item.state || '-' }}</span></td>
                  <td class="strong-cell">{{ form.mode === 'awarded' ? (item.supplier || '-') : (item.agency || '-') }}</td>
                  <td class="object-cell">{{ item.object || '-' }}</td>
                  <td class="keyword-cell">{{ (item.matchedKeywords || []).join(', ') || '-' }}</td>
                  <td>{{ formatDate(form.mode === 'awarded' ? item.signatureDate : item.closingDate) }}</td>
                  <td class="right strong-cell">{{ formatCurrency(item.estimatedValue) }}</td>
                  <td class="right">
                    <a v-if="item.pncpLink" :href="item.pncpLink" target="_blank" rel="noreferrer" @click.stop>
                      <svg viewBox="0 0 24 24"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><path d="M15 3h6v6"/><path d="M10 14L21 3"/></svg>
                    </a>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-else-if="activeView === 'search'" class="empty-state">
          <div>
            <svg viewBox="0 0 24 24"><circle cx="11" cy="11" r="8"/><path d="M21 21l-4.35-4.35"/></svg>
          </div>
          <strong>Pronto para buscar</strong>
          <p>Selecione o modo, período e estados para consultar o PNCP pelo backend Java.</p>
        </section>
      </main>
    </section>

    <div v-if="selected" class="drawer-backdrop" @click="selected = null">
      <aside class="drawer" @click.stop>
        <header>
          <span class="state-pill">{{ selected.state || '-' }}</span>
          <button type="button" @click="selected = null">x</button>
        </header>
        <h2>{{ form.mode === 'awarded' ? (selected.supplier || selected.agency) : selected.agency }}</h2>
        <p>{{ selected.modality || 'Modalidade nao informada' }}</p>

        <div class="drawer-value">
          <span>{{ form.mode === 'awarded' ? 'Valor contratado' : 'Valor estimado' }}</span>
          <strong>{{ formatCurrency(selected.estimatedValue) }}</strong>
        </div>

        <dl>
          <div>
            <dt>N controle PNCP</dt>
            <dd>{{ selected.pncpControlNumber || '-' }}</dd>
          </div>
          <div v-if="form.mode === 'awarded'">
            <dt>CNPJ fornecedor</dt>
            <dd>{{ selected.supplierDocument || '-' }}</dd>
          </div>
          <div>
            <dt>Publicacao</dt>
            <dd>{{ formatDate(selected.publicationDate) }}</dd>
          </div>
          <div>
            <dt>{{ form.mode === 'awarded' ? 'Assinatura' : 'Encerramento' }}</dt>
            <dd>{{ formatDate(form.mode === 'awarded' ? selected.signatureDate : selected.closingDate) }}</dd>
          </div>
        </dl>

        <section>
          <h3>Objeto</h3>
          <p>{{ selected.object || '-' }}</p>
        </section>

        <section v-if="selected.matchedKeywords?.length">
          <h3>Keywords detectadas</h3>
          <div class="drawer-tags">
            <span v-for="keyword in selected.matchedKeywords" :key="keyword">{{ keyword }}</span>
          </div>
        </section>

        <a v-if="selected.pncpLink" class="drawer-link" :href="selected.pncpLink" target="_blank" rel="noreferrer">
          Abrir no PNCP
        </a>
      </aside>
    </div>
  </div>
</template>
