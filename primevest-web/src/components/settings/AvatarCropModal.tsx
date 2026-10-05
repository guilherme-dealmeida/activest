import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Modal } from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';
import { AVATAR_MIN_PX } from '@/utils/avatar';

const SAIDA = 512;
const VIEW = 280;

interface Props {
  file: File | null;
  onClose: () => void;
  /** Recebe o JPEG 512×512 recortado. Rejeitar aqui mantém o modal aberto. */
  onConfirm: (image: Blob) => Promise<void>;
}

/**
 * Recorte da foto de perfil (adendo de 05/10/2026): área quadrada com máscara redonda, arrastar para
 * posicionar e zoom. O navegador gera o JPEG 512×512 — sem os metadados do arquivo original — e a API
 * confere tudo de novo.
 */
export const AvatarCropModal = ({ file, onClose, onConfirm }: Props) => {
  const [img, setImg] = useState<HTMLImageElement | null>(null);
  const [erro, setErro] = useState('');
  const [zoom, setZoom] = useState(1);
  const [pos, setPos] = useState({ x: 0, y: 0 });
  const [busy, setBusy] = useState(false);
  const arrasto = useRef<{ x: number; y: number; px: number; py: number } | null>(null);

  useEffect(() => {
    setImg(null);
    setErro('');
    setZoom(1);
    if (!file) return;
    const url = URL.createObjectURL(file);
    const el = new Image();
    el.onload = () => {
      if (Math.min(el.naturalWidth, el.naturalHeight) < AVATAR_MIN_PX) {
        setErro(`A imagem é pequena demais: precisa de pelo menos ${AVATAR_MIN_PX}×${AVATAR_MIN_PX} px.`);
        return;
      }
      setImg(el);
      const base = VIEW / Math.min(el.naturalWidth, el.naturalHeight);
      setPos({ x: (VIEW - el.naturalWidth * base) / 2, y: (VIEW - el.naturalHeight * base) / 2 });
    };
    el.onerror = () => setErro('Não foi possível abrir a imagem.');
    el.src = url;
    return () => URL.revokeObjectURL(url);
  }, [file]);

  const escala = img ? (VIEW / Math.min(img.naturalWidth, img.naturalHeight)) * zoom : 1;
  const largura = img ? img.naturalWidth * escala : 0;
  const altura = img ? img.naturalHeight * escala : 0;

  /** A imagem sempre cobre a área inteira: sem faixa vazia na foto. */
  const limitar = useCallback((x: number, y: number, w = largura, h = altura) => ({
    x: Math.min(0, Math.max(VIEW - w, x)),
    y: Math.min(0, Math.max(VIEW - h, y)),
  }), [largura, altura]);

  const mudarZoom = (novo: number) => {
    if (!img) return;
    const base = VIEW / Math.min(img.naturalWidth, img.naturalHeight);
    // mantém o ponto do centro da área no lugar
    const cx = (VIEW / 2 - pos.x) / escala;
    const cy = (VIEW / 2 - pos.y) / escala;
    const e = base * novo;
    setZoom(novo);
    setPos(limitar(VIEW / 2 - cx * e, VIEW / 2 - cy * e, img.naturalWidth * e, img.naturalHeight * e));
  };

  const onPointerDown = (e: React.PointerEvent) => {
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
    arrasto.current = { x: e.clientX, y: e.clientY, px: pos.x, py: pos.y };
  };
  const onPointerMove = (e: React.PointerEvent) => {
    const a = arrasto.current;
    if (!a) return;
    setPos(limitar(a.px + e.clientX - a.x, a.py + e.clientY - a.y));
  };
  const onPointerUp = () => { arrasto.current = null; };

  const confirmar = async () => {
    if (!img) return;
    setBusy(true);
    setErro('');
    try {
      const canvas = document.createElement('canvas');
      canvas.width = SAIDA;
      canvas.height = SAIDA;
      const ctx = canvas.getContext('2d')!;
      ctx.fillStyle = '#ffffff'; // PNG transparente: o JPEG não tem alfa
      ctx.fillRect(0, 0, SAIDA, SAIDA);
      ctx.imageSmoothingQuality = 'high';
      const lado = VIEW / escala;
      ctx.drawImage(img, -pos.x / escala, -pos.y / escala, lado, lado, 0, 0, SAIDA, SAIDA);
      const blob = await new Promise<Blob | null>((ok) => canvas.toBlob(ok, 'image/jpeg', 0.9));
      if (!blob) throw new Error('canvas');
      await onConfirm(blob);
    } catch (e) {
      setErro(e instanceof Error && e.message !== 'canvas' ? e.message : 'Não foi possível salvar a foto.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal isOpen={!!file} onClose={onClose} title="Ajustar foto de perfil"
      footer={
        <div className="flex justify-end gap-3">
          <Button variant="ghost" type="button" onClick={onClose}>Cancelar</Button>
          <Button type="button" onClick={confirmar} isLoading={busy} disabled={!img}>Salvar foto</Button>
        </div>
      }>
      <div className="flex flex-col items-center gap-4">
        {erro && <p className="w-full text-sm text-danger">{erro}</p>}
        <div
          className="relative overflow-hidden rounded-md bg-black touch-none select-none cursor-grab active:cursor-grabbing"
          style={{ width: VIEW, height: VIEW }}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
        >
          {img && (
            <img src={img.src} alt="" draggable={false}
              className="absolute max-w-none pointer-events-none"
              style={{ left: pos.x, top: pos.y, width: largura, height: altura }} />
          )}
          {/* máscara redonda: o que fica fora do círculo não aparece no perfil */}
          <div className="pointer-events-none absolute inset-0 rounded-full"
            style={{ boxShadow: '0 0 0 9999px rgba(0,0,0,0.55)', border: '2px solid rgba(201,168,76,0.8)' }} />
        </div>
        <label className="flex w-full max-w-[280px] items-center gap-3 text-xs text-gray-400">
          Zoom
          <input type="range" min={1} max={3} step={0.01} value={zoom} disabled={!img}
            onChange={(e) => mudarZoom(Number(e.target.value))} className="flex-1 accent-accent" />
        </label>
        <p className="text-xs text-gray-500 text-center">Arraste para posicionar. A foto é salva quadrada, em 512×512 px.</p>
      </div>
    </Modal>
  );
};
