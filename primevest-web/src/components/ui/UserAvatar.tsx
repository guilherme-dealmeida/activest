import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';
import { useAvatarSrc } from '@/hooks/useAvatarSrc';
import { User } from '@/types';

/** A foto do usuário, ou a inicial do nome. Menu lateral e Configurações usam a mesma. */
interface Props {
  user: User | null | undefined;
  className?: string;
  textClassName?: string;
  title?: string;
}

export const UserAvatar = ({ user, className, textClassName, title }: Props) => {
  const src = useAvatarSrc(user);
  const base = twMerge(clsx('shrink-0 rounded-full border border-accent/30 overflow-hidden', className));
  if (src) {
    return <img src={src} alt="" title={title} referrerPolicy="no-referrer" className={clsx(base, 'object-cover')} />;
  }
  return (
    <div className={clsx(base, 'bg-accent/20 flex items-center justify-center text-accent font-semibold')} title={title}>
      <span className={textClassName}>{(user?.name?.trim()?.[0] ?? '?').toUpperCase()}</span>
    </div>
  );
};
